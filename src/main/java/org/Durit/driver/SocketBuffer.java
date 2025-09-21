package org.Durit.driver;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public class SocketBuffer implements Closeable {

    private final DatagramChannel channel;
    private final InetSocketAddress localBind;
    private final InetSocketAddress target;
    private final boolean connectedMode;
    private int timeoutMs = 5000; // ajuste conforme rede

    public static final byte BOOT_CMD = (byte) 0x40;
    public static final byte BOOT_OK = (byte) 0xF0;
    public static final byte BOOT_ERR = (byte) 0xF4;

    public record RxPacket(boolean ok, byte boot, byte cmd, Integer address, byte[] data, Integer errCode,
                           byte checksum) {
    }

    public SocketBuffer(String hostLocalIp, int hostLocalPort,
                        String targetIp, int targetPort,
                        boolean connectedMode) throws IOException {
        this.localBind = new InetSocketAddress(InetAddress.getByName(hostLocalIp), hostLocalPort);
        this.target = new InetSocketAddress(InetAddress.getByName(targetIp), targetPort);
        this.connectedMode = connectedMode;

        this.channel = DatagramChannel.open(StandardProtocolFamily.INET);
        this.channel.configureBlocking(false);

        // Habilitar REUSEADDR antes do bind
        this.channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

        // Bind local explícito
        this.channel.bind(localBind);

        // Opcional: "fixar" o destino (evita perder resposta por race e simplifica receive)
        if (connectedMode) {
            this.channel.connect(target);
        }
    }

    /**
     * Timeout de leitura em milissegundos
     */
    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = Math.max(1, timeoutMs);
    }

    /**
     * Monta pacote: 0x40, len, cmd[, address], params..., cks
     */
    public static byte[] buildPacket(byte cmd, Integer address, byte[] params) {
        Objects.requireNonNull(params, "params");
        // Length = bytes de (cmd + [address?] + params)
        int len = 1 + (address != null ? 1 : 0) + params.length;
        ByteBuffer b = ByteBuffer.allocate(1 + 1 + len + 1);
        b.put(BOOT_CMD);
        b.put((byte) (len & 0xFF));
        b.put(cmd);
        if (address != null) b.put((byte) (address & 0xFF));
        b.put(params);
        // checksum sobre [boot..último parâmetro]
        byte[] forCks = Arrays.copyOfRange(b.array(), 0, 1 + 1 + len); // boot + len + payload
        b.put(checksum(forCks));
        return b.array();
    }

    /**
     * Envia e recebe resposta validando length e checksum.
     */
    public RxPacket sendAndReceive(byte[] packet) throws IOException {
        Objects.requireNonNull(packet, "packet");
        if (packet.length < 4) throw new IllegalArgumentException("Pacote muito curto");

        // Selector e OP_READ ANTES de enviar (evita perder resposta rápida)
        try (Selector selector = Selector.open()) {
            channel.register(selector, SelectionKey.OP_READ);

            // Enviar
            ByteBuffer tx = ByteBuffer.wrap(packet);
            if (connectedMode) {
                while (tx.hasRemaining()) channel.write(tx);
            } else {
                while (tx.hasRemaining()) channel.send(tx, target);
            }

            // Aguardar eventos
            int n = selector.select(timeoutMs);
            if (n == 0) {
                throw new SocketTimeoutException("Timeout aguardando resposta UDP (" + timeoutMs + " ms)");
            }

            // Receber
            ByteBuffer rx = ByteBuffer.allocate(2048);
            SocketAddress from;
            if (connectedMode) {
                from = channel.receive(rx);
            } else {
                from = channel.receive(rx);
                // opcional: validar origem (IP/porta do leitor)
                if (from instanceof InetSocketAddress isa) {
                    if (!isa.getAddress().equals(target.getAddress()) || isa.getPort() != target.getPort()) {
                        // chegou de outro host; ignore e continue esperando dentro do mesmo timeout?
                        // Para simplicidade: seguimos com o primeiro pacote recebido.
                    }
                }
            }
            rx.flip();
            byte[] raw = new byte[rx.remaining()];
            rx.get(raw);

            // Parse básico
            if (raw.length < 4) {
                throw new IOException("Resposta muito curta: " + hex(raw));
            }

            byte boot = raw[0];
            int len = raw[1] & 0xFF;

            int expectedTotal = 1 /*boot*/ + 1 /*len*/ + len /*payload*/ + 1 /*cksum*/;
            if (raw.length != expectedTotal) {
                throw new IOException("Length mismatch: esperado " + expectedTotal + " bytes, veio " + raw.length + " (" + hex(raw) + ")");
            }

            // Validar checksum
            byte calc = checksum(Arrays.copyOf(raw, raw.length - 1));
            byte gotCks = raw[raw.length - 1];
            if (calc != gotCks) {
                throw new IOException("Checksum inválido: calc=" + toHex(calc) + " resp=" + toHex(gotCks) + " raw=" + hex(raw));
            }

            // Payload
            int p = 2;
            byte cmd = raw[p++];

            Integer address = null;
            // Heurística: respostas “com endereço” têm um byte de address após o cmd.
            // Você pode saber pelo contexto do comando enviado; aqui tentamos inferir:
            // Se o payload for >=2 e o penúltimo campo não parecer "apenas dados", aceitar address opcional.
            // Para evitar inferência frágil, oferecemos duas leituras:
            // 1) tentar com address
            if (len >= 2) {
                address = raw[p++] & 0xFF;
                byte[] data1 = Arrays.copyOfRange(raw, p, 2 + len);
                // Se a resposta OK/ERR com "address" costuma ter tamanho +1, isso funcionará para a maioria dos comandos “with address”. :contentReference[oaicite:2]{index=2}
                if (boot == BOOT_OK) {
                    return new RxPacket(true, boot, cmd, address, data1, null, gotCks);
                } else if (boot == BOOT_ERR) {
                    Integer err = data1.length >= 1 ? (data1[0] & 0xFF) : null;
                    return new RxPacket(false, boot, cmd, address, data1, err, gotCks);
                }
            }

            // 2) fallback: sem address
            p = 3; // resetar posição como se não houvesse address
            byte[] data0 = Arrays.copyOfRange(raw, p, 2 + len);
            if (boot == BOOT_OK) {
                return new RxPacket(true, boot, cmd, null, data0, null, gotCks);
            } else if (boot == BOOT_ERR) {
                Integer err = data0.length >= 1 ? (data0[0] & 0xFF) : null;
                return new RxPacket(false, boot, cmd, null, data0, err, gotCks);
            }

            // Bootcode inesperado
            return new RxPacket(false, boot, cmd, null, data0, null, gotCks);
        }
    }

    /**
     * Envia um comando genérico construindo o pacote para você.
     */
    public RxPacket sendCommand(byte cmd, Integer address, byte[] params) throws IOException {
        byte[] pkt = buildPacket(cmd, address, params);
        return sendAndReceive(pkt);
    }

    /**
     * Checksum: soma de todos os bytes, complemento de dois ( (~sum)+1 ).
     */
    public static byte checksum(byte[] bytes) {
        int sum = 0;
        for (byte b : bytes) sum = (sum + (b & 0xFF)) & 0xFF;
        int twos = ((~sum) + 1) & 0xFF;
        return (byte) twos;
    }

    public static String hex(byte[] a) {
        return HexFormat.of().formatHex(a).toUpperCase();
    }

    public static String toHex(byte b) {
        return String.format("%02X", b);
    }

    @Override
    public void close() throws IOException {
        try {
            channel.disconnect();
        } catch (Exception ignore) {
        }
        channel.close();
    }

    public static void main(String[] args) throws Exception {
        // Ajuste os IPs/portas conforme "Get Reader Network Address" (cmd 0x31) lido pela serial. :contentReference[oaicite:3]{index=3}
        String hostIp   = "192.168.0.10";  // IP do PC
        int hostPort    = 5000;            // porta local (libere no firewall)
        String readerIp = "192.168.0.100";  // IP do leitor
        int readerPort  = 1713;            // porta UDP do leitor (confirme no equipamento)

        try (SocketBuffer sb = new SocketBuffer(hostIp, hostPort, readerIp, readerPort, true)) {
            sb.setTimeoutMs(6000);

            // Exemplo: Get Reader Version (cmd 0x02, sem params)
            RxPacket v = sb.sendCommand((byte)0x02, null, new byte[0]);
            System.out.println("Versao boot=" + toHex(v.boot) + " ok=" + v.ok + " cmd=" + toHex(v.cmd) +
                    " data=" + hex(v.data) + " err=" + v.errCode);

            // Exemplo: AutoMode (se seu firmware aceitar cmd 0x0F com 1 byte de modo)
            // byte modo = 0x00; // timing/command mode conforme seu FW
            // RxPacket auto = sb.sendCommand((byte)0x0F, null, new byte[]{modo});
            // System.out.println("AutoMode resp: " + (auto.ok ? "OK" : "ERR " + auto.errCode));
        }
    }
}
