package org.Yan.driver;

import com.fazecast.jSerialComm.SerialPort;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @deprecated Use {@link WirelessReader} no lugar.
 * Removido a partir da versão 2.0.
 */
public class Reader {

    // ===== Constantes do legado =====
    private ServerSocket tcpServer;
    private Socket tcpSocket;
    private InputStream netIn;
    private OutputStream netOut;
    public static final int _OK          = 0;
    public static final int _comm_error  = 131;
    public static final int _net_error   = 134;
    private final String _ETIQUETA = "E280691500004021E743F052";
    private HashMap<String, String> bancoDeTags = new HashMap<>();
    static DatagramChannel gchannel;
    static DatagramSocket gsocket;
    private static final int MAX_PACKET_LEN = 262;
    static String TargetAdress = "192.168.0.100";
    static int TargetPort = 1713;
    private static final int TIMEOUT_MS     = 2000; // TimeOut do legado
    private static boolean bound = false;
    private Transport transport = Transport.TCP; // use TCP enquanto estiver com ServerSocket/Socket

    // ===== Estado serial (jSerialComm) =====
    private SerialPort port;
    private InputStream in;
    private OutputStream out;
    public record RxPacket(boolean ok, byte boot, byte cmd, Integer address, byte[] data, Integer errCode, byte checksum) {}

    // ---------- Abertura / Fechamento ----------
    public boolean open(SerialPort portName, int baudrate) {
        port = portName;
        port.setComPortParameters(baudrate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, TIMEOUT_MS, 0);
        if (!port.openPort()) return false;
        in = port.getInputStream();
        out = port.getOutputStream();
        return true;
    }

    public void close() {
        try { if (in  != null) in.close();  } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        if (port != null) port.closePort();
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    // ---------- Métodos “espelho” do legado ----------
    public int AutoMode(int hScanner, int Mode, int Address) {
        int res = 0;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];
        try {
            if (Address == 0) { cmd[0]=3; cmd[1]=15; cmd[2]=(byte)Mode; cmd[3]=0; }
            else              { cmd[0]=4; cmd[1]=15; cmd[2]=(byte)Address; cmd[3]=(byte)Mode; cmd[4]=0; }

            if (!Packet(hScanner, cmd, ret)) return _net_error;
            if (ret[0] == (byte)0xF4) return (ret[1] & 0xFF); // erro
            // sucesso (0xF0)
        } catch (Exception e) {
            e.printStackTrace(); res = -1;
        }
        return res;
    }


    public int Net_AutoMode(int hScanner, int Mode, int Address) {
        int res = 0;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];
        try {
            if (Address == 0) { cmd[0]=3; cmd[1]=15; cmd[2]=(byte)Mode; cmd[3]=0; }
            else              { cmd[0]=4; cmd[1]=15; cmd[2]=(byte)Address; cmd[3]=(byte)Mode; cmd[4]=0; }

            if (!Net_PacketX(hScanner, cmd, ret)) return _net_error;
            if (ret[0] == (byte)0xF4) return (ret[1] & 0xFF); // erro
            return _OK;
        } catch (Exception e) {
            e.printStackTrace(); res = -1;
        }
        return res;
    }

    public int GetSerialData() {
        try {
            if (in == null) return 0;
            if (in.available() > 0) {
                int b = in.read();
                return (b < 0) ? 0 : b;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    public void setNetIn(InputStream netIn) {
        this.netIn = netIn;
    }

    public void setNetOut(OutputStream netOut) {
        this.netOut = netOut;
    }

    public int ReadBasicParam(int hScanner, byte[] pParam, int Address) {
        int res = _OK;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];
        try {
            if (Address == 0) { cmd[0]=2; cmd[1]=6; cmd[2]=0; }
            else              { cmd[0]=3; cmd[1]=6; cmd[2]=(byte)Address; cmd[3]=0; }

            if (!Packet(hScanner, cmd, ret)) return _net_error;
            if (ret[0] == (byte)0xF4) return (ret[1] & 0xFF);

            if (Address != 0) System.arraycopy(ret, 2, pParam, 0, 32);
            else              System.arraycopy(ret, 1, pParam, 0, 32);
            res = _OK;
        } catch (Exception e) { e.printStackTrace(); res = -1; }
        return res;
    }

    public int getReaderNet(int hScanner, byte[] ip, int[] port, byte[] mask, byte[] gateway){
        int res = _OK;
        byte [] cmd = new byte[MAX_PACKET_LEN];
        byte [] ret = new byte[MAX_PACKET_LEN];

        try{
            cmd[0] = 2;
            cmd[1] = 49;
            cmd[2] = 0;
           if(!Packet(hScanner, cmd, ret)){
               return _comm_error;
           }

            System.arraycopy(ret, 1, ip, 0, 4);
            port[0] = (ret[5] << 8) + ret[6];
            System.arraycopy(ret, 7, mask, 0, 4);
            System.arraycopy(ret, 11, gateway, 0, 4);

            String ipAdress = (ip[0] & 0xFF) + "." +
                    (ip[1] & 0xFF) + "." +
                    (ip[2] & 0xFF) + "." +
                    (ip[3] & 0xFF);

            String maskStr = (mask[0] & 0xFF) + "." + (mask[1] & 0xFF) + "." + (mask[2] & 0xFF) + "." + (mask[3] & 0xFF);
            String gwStr = (gateway[0] & 0xFF) + "." + (gateway[1] & 0xFF) + "." + (gateway[2] & 0xFF) + "." + (gateway[3] & 0xFF);

            System.out.println("IP=" + ipAdress + "  Mask=" + maskStr + "  GW=" + gwStr + "  Port=" + port[0]);

            res = _OK;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Erro ao Obter Informações de Rede");
        }
        return res;
    }

    public int WriteBasicParam(int hScanner, byte[] pParam, int Address) {
        int res = _OK;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];
        int n = 32;
        try {
            if (Address == 0) {
                cmd[0] = (byte)(2 + n); cmd[1] = 9;
                System.arraycopy(pParam, 0, cmd, 2, n);
                cmd[2 + n] = 0;
            } else {
                cmd[0] = (byte)(3 + n); cmd[1] = 9; cmd[2]=(byte)Address;
                System.arraycopy(pParam, 0, cmd, 3, n);
                cmd[3 + n] = 0;
            }

            if (!Packet(hScanner, cmd, ret)) return _comm_error;
            if (ret[0] == (byte)0xF4) return (ret[1] & 0xFF);
            res = _OK;
        } catch (Exception e) { e.printStackTrace(); res = -1; }
        return res;
    }

    public  int Net_ConnectScanner(int[] hScanner, String nTargetAddress, int nTargetPort, String nHostAddress, int nHostPort) {
        int i = 0;

        int res;
        try {

            gchannel = DatagramChannel.open(StandardProtocolFamily.INET);
            gsocket  = gchannel.socket();
            gchannel.configureBlocking(false);
            gchannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            try { gchannel.setOption(StandardSocketOptions.SO_BROADCAST, true); } catch (Exception ignore) {}

// >>> bind em 0.0.0.0:porta (wildcard) para receber broadcast <<<
            SocketAddress saHost = new InetSocketAddress(nHostPort);  // NÃO use nHostAddress aqui
            if (!gsocket.isBound() && !bound) {
                gsocket.bind(saHost);
                bound = true;
            }
            TargetAdress = nTargetAddress;
            TargetPort = nTargetPort;

            res = Net_AutoMode(hScanner[0], 0);
            if(res!= _OK){
                System.err.println("Falha ao iniciar modo de rede");
                return res;
            }

            System.out.println("Conectado ao cabo de rede com sucesso!!");
            res = _OK;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception of Net_ConnectScanner:\n");
            res = -1;
            gchannel = null;
        }

        return res;
    }

    public int Net_AutoMode(int hScanner, int Mode){
        int res = 0;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];

        try{
            cmd[0] = 3;
            cmd[1] = 15;
            cmd[2] = (byte)Mode;
            cmd[3] = 0;
            if(!Net_Packet(hScanner, cmd, ret)){
                return _net_error;
            }
            if(ret[0] == (byte) 0xF4){
                return (ret[1] & 0xFF);
            }

        }catch (Exception e){
            e.printStackTrace();
            res = -1;
        }
        return res;
    }

    public int Network_GetReaderVersion(int hScanner,short[] wHardVer,short[] wSoftVer){
        int res = _OK;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];

        try {
                cmd[0] = 2;
                cmd[1] = 2;
                cmd[2] = 0;

            if (!Net_Packet(hScanner, cmd, ret)) {
                return _net_error;
            }
            ;
            if (ret[0] == -12) {
                return ret[1];
            }


                wHardVer[0] = (short) (ret[2] * 256 + ret[3]);
                wSoftVer[0] = (short) (ret[4] * 256 + ret[5]);


            res = _OK;
        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Falha ao obter versao do Leitor");
            res = -1;
        }
        return res;

    }

    public int getReaderVersion(int hScanner,short[] wHardVer,short[] wSoftVer, int adress){
        int res = _OK;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];

        try {
            if (adress == 0) {
                cmd[0] = 2;
                cmd[1] = 2;
                cmd[2] = 0;
            }
            if (!Packet(hScanner, cmd, ret)) {
                return _comm_error;
            }
            ;
            if (ret[0] == -12) {
                return ret[1];
            }

            if (adress == 0) {
                wHardVer[0] = (short) (ret[2] * 256 + ret[3]);
                wSoftVer[0] = (short) (ret[4] * 256 + ret[5]);
            }

            res = _OK;
        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Falha ao obter versao do Leitor");
            res = -1;
        }
        return res;

    }

    public int ReadAutoParam(int hScanner, byte[] pParam, int Address) {
        int res = _OK;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];
        try {
            if (Address == 0) { cmd[0]=2; cmd[1]=20; cmd[2]=0; }
            else              { cmd[0]=3; cmd[1]=20; cmd[2]=(byte)Address; cmd[3]=0; }

            if (!Packet(hScanner, cmd, ret)) return _net_error;
            if (ret[0] == (byte)0xF4) return (ret[1] & 0xFF);

            if (Address != 0) System.arraycopy(ret, 2, pParam, 0, 32);
            else              System.arraycopy(ret, 1, pParam, 0, 32);
            res = _OK;
        } catch (Exception e) { e.printStackTrace(); res = -1; }
        return res;
    }

    public int WriteAutoParam(int hScanner, byte[] pParam, int Address) {
        int res = _OK;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];
        int n = 32;
        try {
            if (Address == 0) {
                cmd[0]=(byte)(2+n); cmd[1]=19;
                System.arraycopy(pParam,0,cmd,2,n);
                cmd[2+n]=0;
            } else {
                cmd[0]=(byte)(3+n); cmd[1]=19; cmd[2]=(byte)Address;
                System.arraycopy(pParam,0,cmd,3,n);
                cmd[3+n]=0;
            }

            if (!Packet(hScanner, cmd, ret)) return _comm_error;
            if (ret[0] == (byte)0xF4) return (ret[1] & 0xFF);
            res = _OK;
        } catch (Exception e) { e.printStackTrace(); res = -1; }
        return res;
    }

    public int EPC1G2_DetectTag(int hScanner, int Address) {
        int res = _OK;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];
        try {
            if (Address == 0) { cmd[0]=2; cmd[1]=(byte)0xEF; cmd[2]=0; }
            else              { cmd[0]=3; cmd[1]=(byte)0xEF; cmd[2]=(byte)Address; cmd[3]=0; }

            if (!Packet(hScanner, cmd, ret)) return _net_error;
            if (ret[0] == (byte)0xF4) return (ret[1] & 0xFF);
            // sucesso (0xF0) -> payload em ret[1..] conforme Packet()
            res = _OK;
        } catch (Exception e) { e.printStackTrace(); res = -1; }
        return res;
    }

    public boolean Net_Packet(int hScanner, byte[] cmd, byte[] res) {
        boolean fRes = false;

        // defensivo
        if (cmd == null || res == null || cmd.length == 0) return false;

        byte[] SendTemp    = new byte[MAX_PACKET_LEN];
        byte[] ReceiveTemp = new byte[MAX_PACKET_LEN];

        try {
            // ---- montar pacote TX: [0x40][len=cmd[0]][payload...][checksum] ----
            int bufferLength = (cmd[0] & 0xFF) + 2; // boot(1) + len(1) + payload(len) + checksum(1)  → o checksum será incluído abaixo
            if (bufferLength > MAX_PACKET_LEN) return false;

            SendTemp[0] = (byte)0x40;              // boot
            byte checksum = (byte)0x40;            // soma começa no boot

            int i;
            for (i = 0; i < (cmd[0] & 0xFF); i++) {
                SendTemp[i + 1] = cmd[i];          // copia len e payload (cmd[0] já é o len)
                checksum += SendTemp[i + 1];
            }
            // two's complement
            checksum = (byte) (~checksum + 1);
            SendTemp[i + 1] = checksum;            // checksum no fim

            // ---- enviar e receber ----
            if (!SocketBuffer(hScanner, SendTemp, ReceiveTemp, bufferLength)) {
                return false;
            }

            // ---- parse RX robusto ----
            // Formato: [boot(F0/F4)][len][cmdEco][dados...(len-1)][checksum?]
            // Não dependemos do checksum para calcular tamanhos/cópias.
            int len = ReceiveTemp[1] & 0xFF;   // tamanho do payload (cmd + dados)
            if (len < 1) return false;        // precisa ter pelo menos o cmd ecoado

            byte boot    = ReceiveTemp[0];    // F0=OK, F4=ERR
            byte cmdResp = ReceiveTemp[2];    // eco do comando
            byte cmdSent = SendTemp[2];       // comando enviado

            // valida eco (opcional mas recomendado)
            if (cmdResp != cmdSent) return false;

            int dataLen = len - 1;            // dados após o cmd
            if (dataLen < 0) dataLen = 0;

            // garantir que não vamos estourar 'res'
            int maxCopy = Math.max(0, Math.min(dataLen, res.length - 1));

            if (boot == (byte)0xF0) {
                // OK: res[0]=F0, res[1..] = dados
                res[0] = (byte)0xF0;
                if (maxCopy > 0) {
                    System.arraycopy(ReceiveTemp, 3, res, 1, maxCopy);
                }
                fRes = true;

            } else if (boot == (byte)0xF4) {
                // ERRO: res[0]=F4, res[1]=errCode (se houver)
                res[0] = (byte)0xF4;
                res[1] = (len >= 2) ? ReceiveTemp[3] : 0;
                fRes = true;

            } else {
                // boot inesperado
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Erro ao enviar/receber pacote de Rede");
            return false;
        }

        return fRes;
    }

    private boolean SocketBuffer(int hScanner, byte[] sendTemp, byte[] receiveTemp, int bufferLength) {
        boolean fRes =false;

        try {
            DatagramChannel channel = gchannel;

            byte[] chTemp = new byte[bufferLength];
            byte[] lpSndTemp = new byte[bufferLength * 4];
            System.arraycopy(sendTemp, 0, chTemp, 0, bufferLength);
            ByteBuffer sendBuf = ByteBuffer.allocate(chTemp.length);
            sendBuf.put(chTemp);
            sendBuf.flip();
            String[] SsTemp = TargetAdress.split("\\.");
            byte[] bs = new byte[]{(byte)Integer.parseInt(SsTemp[0]), (byte)Integer.parseInt(SsTemp[1]), (byte)Integer.parseInt(SsTemp[2]), (byte)Integer.parseInt(SsTemp[3])};
            InetAddress address = InetAddress.getByAddress(bs);
            SocketAddress sa = new InetSocketAddress(address, TargetPort);
            Bcd2AscEx(lpSndTemp, chTemp, bufferLength * 2);
            String snd = new String(lpSndTemp, "UTF-8");
            Calendar cd = Calendar.getInstance();
            String year = addZero(cd.get(1));
            String month = addZero(cd.get(2) + 1);
            String day = addZero(cd.get(5));
            String hour = addZero(cd.get(11));
            String min = addZero(cd.get(12));
            String sec = addZero(cd.get(13));
            String mil = addZero3(cd.get(14));
            String strOutput = "【" + year + month + day + " " + hour + ":" + min + ":" + sec + "." + mil + "】--- SenA[" + addZero4(bufferLength) + "]:" + snd.trim();
            System.out.println(""+ strOutput);

            channel.send(sendBuf, sa);
            Selector selector = null;
            selector = Selector.open();
            channel.register(selector, 1);
            int iRecvLen = 0;
            ByteBuffer byteBuffer = ByteBuffer.allocate(65535);
            byte[] lpRecvTemp = new byte[1024];
            int eventsCount = selector.select((long)TIMEOUT_MS);
            if (eventsCount > 0) {
                Set selectedKeys = selector.selectedKeys();
                Iterator iterator = selectedKeys.iterator();

                while(iterator.hasNext()) {
                    SelectionKey sk = (SelectionKey)iterator.next();
                    iterator.remove();
                    if (sk.isReadable()) {
                        DatagramChannel datagramChannel = (DatagramChannel)sk.channel();
                        byteBuffer.clear();
                        datagramChannel.receive(byteBuffer);
                        byteBuffer.flip();
                        iRecvLen = byteBuffer.limit();
                        CharBuffer charBuffer = Charset.forName("UTF-8").decode(byteBuffer);
                        System.arraycopy(byteBuffer.array(), 0, receiveTemp, 0, iRecvLen);
                        Bcd2AscEx(lpRecvTemp, byteBuffer.array(), iRecvLen * 2);
                        String rev = new String(lpRecvTemp, "UTF-8");
                        Calendar cdx = Calendar.getInstance();
                        String yearx = addZero(cdx.get(1));
                        String monthx = addZero(cdx.get(2) + 1);
                        String dayx = addZero(cdx.get(5));
                        String hourx = addZero(cdx.get(11));
                        String minx = addZero(cdx.get(12));
                        String secx = addZero(cdx.get(13));
                        String milx = addZero3(cdx.get(14));
                        strOutput = "【" + yearx + monthx + dayx + " " + hourx + ":" + minx + ":" + secx + "." + milx + "】--- RecA[" + addZero4(iRecvLen) + "]:" + rev.trim();
                        System.out.println(""+ strOutput);
                        byteBuffer.clear();
                        fRes = true;
                    }
                }
            }

            selector.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception of SocketBuffer:\n");
            fRes = false;
        }

        return fRes;
    }

    // ---------- Packet (idêntico ao legado) ----------
    private boolean Packet(int hScanner, byte[] cmd, byte[] return_data) {
        boolean fRes = false;
        byte[] writepk = new byte[MAX_PACKET_LEN];
        byte[] readpk  = new byte[MAX_PACKET_LEN];

        try {
            // --- montar TX: [0]=0x40, [1..len] = cmd[0..len-1], [ult]=checksum tal que soma total==0
            int BufLen = (cmd[0] & 0xFF) + 2;
            if (BufLen > MAX_PACKET_LEN) return false;

            writepk[0] = 0x40; // 64
            byte checksum = 0x40;

            int i;
            for (i = 0; i < (cmd[0] & 0xFF); ++i) {
                writepk[i + 1] = cmd[i];
                //pk começa em 1, pegando o lenght do comando, e o resto dos params do comando, apos isso soma o checksum
                checksum += writepk[i + 1];
            }
            checksum = (byte)(~checksum); // two's complement
            checksum++;
            writepk[i + 1] = checksum;

            // envia/recebe
            if (!SerialBuffer(hScanner, writepk, readpk, BufLen)) return false;

            // --- validar RX: len, checksum soma-para-zero
            BufLen = (readpk[1] & 0xFF) + 2;
            if (BufLen > MAX_PACKET_LEN) return false;

            byte sum = 0;
            for (int k = 0; k < BufLen; ++k) sum += readpk[k];
            if (sum != 0) return false;

            // --- eco de comando (readpk[2]) deve bater com writepk[2] (cmd[1])
            if (writepk[2] == readpk[2]) {
                switch (readpk[0]) {
                    case (byte)0xF0: // sucesso
                        return_data[0] = (byte)0xF0;
                        if (BufLen - 4 > 0) {
                            if (readpk[2] != 22 && readpk[2] != 87) {
                                System.arraycopy(readpk, 3, return_data, 1, BufLen - 4);
                            } else {
                                System.arraycopy(readpk, 1, return_data, 1, BufLen - 2);
                            }
                        }
                        fRes = true;
                        break;
                    case (byte)0xF4: // erro
                        return_data[0] = (byte)0xF4;
                        return_data[1] = readpk[3];
                        fRes = true;
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            fRes = false;
        }
        return fRes;
    }

    public void pararLeitura(){
        int res;
        int hScanner = 0;
        int Address = 0;
        res = AutoMode(hScanner, 0, Address); // 0 = Auto mode padrão do leitor
        if (res == Reader._OK) {
            System.out.println("Stop AutoMode Success!");
        } else {
            System.err.println("Stop AutoMode Fail! Please try again!");
        }
    }




    // ---------- SerialBuffer (RX/TX) compatível com o legado ----------
    private boolean SerialBuffer(int hScanner, byte[] lpPutBuf, byte[] lpGetBuf, int nBufLen) {
        try {
            // log de envio (opcional)
            // send
            out.write(lpPutBuf, 0, nBufLen);
            out.flush();

            // read 4 bytes de cabeçalho: [0]=0xF0/0xF4, [1]=len, [2]=cmd eco, [3]=...
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            byte[] head4 = readExact(in, 4, deadline);

            byte head = head4[0];
            if (head != (byte)0xF0 && head != (byte)0xF4) return false;

            int len = head4[1] & 0xFF;
            int remaining = len - 2; // igual ao decompilado
            if (remaining < 0) remaining = 0;

            byte[] tail = (remaining > 0) ? readExact(in, remaining, deadline) : new byte[0];

            // montar pacote completo em lpGetBuf (para Packet validar soma/len)
            int recvLen = 4 + tail.length; // deve ser len + 2
            if (recvLen > lpGetBuf.length) recvLen = lpGetBuf.length;

            System.arraycopy(head4, 0, lpGetBuf, 0, Math.min(4, recvLen));
            if (recvLen > 4) System.arraycopy(tail, 0, lpGetBuf, 4, recvLen - 4);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void imprimirTags(){
        System.out.println(bancoDeTags);
    }

    public HashMap<String, String> getBancoDeTags() {
        if(open(port, 9600)){
            close();
            return bancoDeTags;
        }
        return bancoDeTags;

    }

    public void iniciarLeituraManual(){
        int hScanner = 0;
        int Address = 0;
        int res;
        res = AutoMode(hScanner, 1, Address); // 1 = Command mode ON (auto test inicia)


        if (res == Reader._OK) {
            System.out.println("Start AutoMode Success!");
            // Simula o MyTimerSerialAuto: consome a serial por alguns segundos
            long end = System.currentTimeMillis() + 3000;
            StringBuilder buf = new StringBuilder(128);

            while (System.currentTimeMillis() < end) {
                int b = this.GetSerialData();
                if (b > 0) {
                    char ch = (char) (b & 0xFF);
                    if (ch == '\r' || ch == '\n') {
                        String line = buf.toString().trim();
                        if (!line.isEmpty()){
                            line.replaceAll("[,\\s]+", "");
                            System.out.println("ID: "+ line);
                        }
                        buf.setLength(0);
                    } else {
                        buf.append(ch);
                    }
                } else {
                    // dá uma folguinha pro CPU
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }

            }
        } else {
            System.err.println("Start AutoMode Fail! Please try again!");
        }
        pararLeitura();

    }

    public Set<String> Net_getDistinctTags() {
        final int hScanner = 0, Address = 0;
        final long until = System.currentTimeMillis() + 1000; // ~0.6s de varredura
        Set<String> tags = new HashSet<>();

        // garante que não estamos em push por rede enquanto fazemos polling
        try { Net_AutoMode(hScanner, 0, Address); } catch (Exception ignore) {}

        byte[] IDBuffer = new byte[1024];
        int[]  nCounter = new int[1];

        while (System.currentTimeMillis() < until) {
            // mem=1 (EPC), ptrBits=0, lenBits=0, sem máscara
            int r = Net_EPC1G2_ReadLabelID(hScanner, 1, 0, 0, new byte[0], IDBuffer, nCounter);

            if (r == _OK && nCounter[0] > 0) {
                int idx = 0;
                for (int i = 0; i < nCounter[0]; i++) {
                    if (idx >= IDBuffer.length) break;
                    int L = IDBuffer[idx] & 0xFF;              // tamanho em WORDS
                    int bytes = 1 + L * 2;                      // [L][EPC...]
                    if (L == 0 || idx + bytes > IDBuffer.length) break;

                    StringBuilder epc = new StringBuilder(L * 4);
                    for (int k = 0; k < L * 2; k++) {
                        epc.append(String.format("%02X", IDBuffer[idx + 1 + k]));
                    }
                    String tag = epc.toString();
                    System.out.println(tag);
                    if (!tag.isEmpty()) tags.add(tag);

                    idx += bytes;
                }
            }
            try { Thread.sleep(200); } catch (InterruptedException ignore) {}
        }
        return tags;
    }

    public Set<String> getDistinctTags(){
        int hScanner = 0;
        int Address = 0;
        int res;
        res = AutoMode(hScanner, 1, Address); // 1 = Command mode ON (auto test inicia)
        Set<String> tags = new HashSet<>();

        if (res == Reader._OK) {
            System.out.println("Start AutoMode Success!");
            // Simula o MyTimerSerialAuto: consome a serial por alguns segundos
            long end = System.currentTimeMillis() + 10000;
            StringBuilder buf = new StringBuilder(128);

            while (System.currentTimeMillis() < end) {
                int b = this.GetSerialData();
                if (b > 0) {
                    char ch = (char) (b & 0xFF);
                    if (ch == '\r' || ch == '\n') {
                        String line = buf.toString().trim();
                       line = line.replaceAll("[,\\s]+", "");
                        if (!line.isEmpty()){
                            tags.add(line);
                        }
                        buf.setLength(0);
                    } else {
                        buf.append(ch);
                    }
                } else {
                    // dá uma folguinha pro CPU
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }

            }
        } else {
            System.err.println("Start AutoMode Fail! Please try again!");
        }
        pararLeitura();
        close();
        return tags;

    }

    public void iniciarLeituraAutomatica(){
        int hScanner = 0;
        int Address = 0;
        int res;
        res = AutoMode(hScanner, 1, Address); // 1 = Command mode ON (auto test inicia)

        if (res == Reader._OK) {
            System.out.println("Start AutoMode Success!");
            // Simula o MyTimerSerialAuto: consome a serial por alguns segundos
            long end = System.currentTimeMillis() + 3000;
            StringBuilder buf = new StringBuilder(128);

            while (System.currentTimeMillis() < end) {
                int b = this.GetSerialData();
                if (b > 0) {
                    char ch = (char) (b & 0xFF);
                    if (ch == '\r' || ch == '\n') {
                        String line = buf.toString().trim();
                        if (!line.isEmpty()){
                            if(!bancoDeTags.containsValue(_ETIQUETA)){
                                bancoDeTags.put("Etiqueta", line);
                            };
                            if(!bancoDeTags.containsKey("Cartao") && !line.contains(_ETIQUETA)){
                                bancoDeTags.put("Cartao", line);
                            }

                        }
                        buf.setLength(0);
                    } else {
                        buf.append(ch);
                    }
                } else {
                    // dá uma folguinha pro CPU
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }

            }
        } else {
            System.err.println("Start AutoMode Fail! Please try again!");
        }
    }
    // ---------- Utilidades ----------
    private static byte[] readExact(InputStream in, int n, long deadlineMs) throws IOException {
        byte[] out = new byte[n];
        int off = 0;
        while (off < n) {
            if (System.currentTimeMillis() > deadlineMs)
                throw new IOException("Serial read timeout (" + n + " bytes)");
            int r = in.read(out, off, n - off);
            if (r < 0) throw new EOFException("Serial closed");
            if (r == 0) {
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                continue;
            }
            off += r;
        }
        return out;
    }

    // (helpers de timestamp só se quiser logar igual ao legado)
    @SuppressWarnings("unused")
    private static String ts() {
        Calendar c = Calendar.getInstance();
        return String.format("【%04d%02d%02d %02d:%02d:%02d.%03d】",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH)+1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
                c.get(Calendar.MILLISECOND));
    }

    public static String addZero(int i) {
        if (i < 10) {
            String tmpString = "0" + i;
            return tmpString;
        } else {
            return String.valueOf(i);
        }
    }

    public static void Bcd2AscEx(byte[] asc, byte[] bcd, int len) {
        int j = (len + len % 2) / 2;
        int k = 3 * j;

        for(int i = 0; i < j; ++i) {
            asc[3 * i] = (byte)(bcd[i] >> 4 & 15);
            asc[3 * i + 1] = (byte)(bcd[i] & 15);
            asc[3 * i + 2] = 32;
        }

        for(int var6 = 0; var6 < k; ++var6) {
            if ((var6 + 1) % 3 != 0) {
                if (asc[var6] > 9) {
                    asc[var6] = (byte)(65 + asc[var6] - 10);
                } else {
                    asc[var6] = (byte)(asc[var6] + 48);
                }
            }
        }

        asc[k] = 0;
    }
    public static String addZero3(int i) {
        if (i < 10) {
            String tmpString = "00" + i;
            return tmpString;
        } else if (i < 100) {
            String tmpString = "0" + i;
            return tmpString;
        } else {
            return String.valueOf(i);
        }
    }

    public static String addZero4(int i) {
        if (i < 10) {
            String tmpString = "000" + i;
            return tmpString;
        } else if (i < 100) {
            String tmpString = "00" + i;
            return tmpString;
        } else if (i < 1000) {
            String tmpString = "0" + i;
            return tmpString;
        } else {
            return String.valueOf(i);
        }
    }

    public int Net_EPC1G2_ListTagID(int hScanner, int mem, int ptrBits, int lenBits,
                                    byte[] mask, byte[] IDBuffer, int[] nCounter, int[] IDlen) {
        byte[] put = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];
        try {
            // bytes de máscara (ceil(lenBits/8)); se lenBits==0, sem máscara
            int m = (lenBits <= 0) ? 0 : (lenBits / 8 + ((lenBits % 8 != 0) ? 1 : 0));
            if (mask == null) mask = new byte[0];
            if (m > mask.length) m = mask.length;

            // 『40H  (m+6)  EEH  mem  ptrHi  ptrLo  lenBits  mask[m]  00』
            put[0] = (byte) (m + 6);
            put[1] = (byte) 0xEE;
            put[2] = (byte) mem;
            put[3] = (byte) (ptrBits >> 8);
            put[4] = (byte) (ptrBits);
            put[5] = (byte) (lenBits);
            for (int i = 0; i < m; i++) put[6 + i] = mask[i];
            put[6 + m] = 0;

            if (!Net_PacketX(hScanner, put, ret)) return _net_error;
            if (ret[0] == (byte)0xF4)            return (ret[1] & 0xFF); // erro

            // Em Net_Packet, ret[0]=F0 e ret[1..] = DATA (cmd já foi removido).
            // DATA(EEh): [M][ L EPC ][ L EPC ] ...
            int posData = 1;
            int out     = 0;

            int M = ret[posData] & 0xFF;
            nCounter[0] = M;
            posData++;

            int maxTags = Math.min(M, 8);
            for (int i = 0; i < maxTags; i++) {
                int L = ret[posData] & 0xFF;          // comprimento em WORDS
                int bytes = 1 + L * 2;                // L + EPC(L*2 bytes)
                if (posData + bytes > ret.length) break;
                if (out + bytes > IDBuffer.length) break;

                System.arraycopy(ret, posData, IDBuffer, out, bytes);
                posData += bytes;
                out     += bytes;
            }
            IDlen[0] = out;
            return _OK;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    // EDh — Busca “páginas” extras quando M > 8.
// stNum = índice inicial (múltiplo de 8). count <= 8.
// Copia blocos [L][EPC...] em btOut; IDlen[0] = bytes válidos copiados.
    public int Net_EPC1G2_GetIDList(int hScanner, byte[] btOut, int stNum, int count, int[] IDlen) {
        byte[] put = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];
        try {
            if (count < 1) { IDlen[0] = 0; return _OK; }
            if (btOut == null) btOut = new byte[0];

            // 『40H  04H  EDH  stNum  count  00』
            put[0] = 4;
            put[1] = (byte) 0xED;
            put[2] = (byte) stNum;
            put[3] = (byte) count;
            put[4] = 0;

            if (!Net_PacketX(hScanner, put, ret)) return _net_error;
            if (ret[0] == (byte)0xF4)            return (ret[1] & 0xFF);

            // DATA(EDh): [ L EPC ][ L EPC ] ... (count grupos)
            int posData = 1;
            int out     = 0;

            for (int i = 0; i < count; i++) {
                if (posData >= ret.length) break;
                int L = ret[posData] & 0xFF;
                int bytes = 1 + L * 2;
                if (posData + bytes > ret.length) break;
                if (out + bytes > btOut.length) break;

                System.arraycopy(ret, posData, btOut, out, bytes);
                posData += bytes;
                out     += bytes;
            }
            IDlen[0] = out;
            return _OK;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    // Agregador — chama EEh e, se M>8, pagina com EDh até completar.
    public int Net_EPC1G2_ReadLabelID(int hScanner, int mem, int ptrBits, int lenBits,
                                      byte[] mask, byte[] IDBuffer, int[] nCounter) {
        try {
            if (IDBuffer == null || nCounter == null || nCounter.length == 0) return -1;

            int[] IDlen  = new int[1];
            int[] extraL = new int[1];
            byte[] extra = new byte[Math.max(1024, IDBuffer.length)];

            nCounter[0] = 0;

            // 1) primeira página (até 8) via EEh
            int r = Net_EPC1G2_ListTagID(hScanner, mem, ptrBits, lenBits, mask, IDBuffer, nCounter, IDlen);
            if (r != _OK) return r;

            // 2) se houver mais de 8, buscar páginas EDh
            int total = nCounter[0];
            if (total > 8) {
                int out = IDlen[0];
                int fullPages = total / 8;
                int rem       = total % 8;

                // páginas 1..(fullPages-1), cada uma com 8
                for (int p = 1; p < fullPages; p++) {
                    r = Net_EPC1G2_GetIDList(hScanner, extra, p * 8, 8, extraL);
                    if (r != _OK) return r;
                    if (out + extraL[0] > IDBuffer.length) break;
                    System.arraycopy(extra, 0, IDBuffer, out, extraL[0]);
                    out += extraL[0];
                }
                // resto (se houver)
                if (rem > 0) {
                    r = Net_EPC1G2_GetIDList(hScanner, extra, fullPages * 8, rem, extraL);
                    if (r != _OK) return r;
                    if (out + extraL[0] <= IDBuffer.length) {
                        System.arraycopy(extra, 0, IDBuffer, out, extraL[0]);
                    }
                }
            }
            return _OK;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public void tryProxy (){

    }
    public int Net_WirelessListen(int listenPort, int acceptTimeoutMs){
        try{
            if(tcpServer != null) tcpServer.close();
            tcpServer = new ServerSocket(listenPort);
            tcpServer.setReuseAddress(true);
            tcpServer.setSoTimeout(acceptTimeoutMs);

            System.out.println("Aguardando conexão");
            tcpSocket = tcpServer.accept();
            System.out.println("Conectado: "+ tcpSocket.getRemoteSocketAddress());
            netIn = tcpSocket.getInputStream();
            netOut = tcpSocket.getOutputStream();
            return _OK;
        }catch (Exception e){
            e.printStackTrace();
            return _net_error;
        }
    }

    private boolean wirelessTcpPacket(int hScanner, byte[] cmd, byte[] res) {
        try {
            if (netIn == null || netOut == null) return false;

            // montar TX: [0x40][len=cmd[0]][payload...][checksum]
            byte[] send = new byte[(cmd[0] & 0xFF) + 2];
            byte sum = 0x40;
            send[0] = 0x40;
            for (int i = 0; i < (cmd[0] & 0xFF); i++) { send[i+1] = cmd[i]; sum += send[i+1]; }
            sum = (byte)(~sum + 1);
            send[send.length - 1] = sum;

            // envia
            netOut.write(send);
            netOut.flush();

            // lê cabeçalho de 4 bytes: [F0/F4][len][cmdEco][...]
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            byte[] head4 = readExact(netIn, 4, deadline);     // você já tem readExact(...)
            byte boot = head4[0];
            int  len  = head4[1] & 0xFF;                      // (cmd + dados)
            int  rest = Math.max(0, len - 2);                 // bytes restantes após [len][cmdEco]
            byte[] body = (rest > 0) ? readExact(netIn, rest, deadline) : new byte[0];

            // montar receiveTemp “virtual” para reaproveitar a lógica:
            byte[] rx = new byte[4 + body.length];
            System.arraycopy(head4, 0, rx, 0, 4);
            if (body.length > 0) System.arraycopy(body, 0, rx, 4, body.length);

            // valida eco
            if (rx[2] != cmd[1]) return false;               // cmd ecoado deve bater

            if (boot == (byte)0xF0) {                        // sucesso
                res[0] = (byte)0xF0;
                int dataLen = Math.max(0, len - 1);
                int copy = Math.min(dataLen, res.length - 1);
                if (copy > 0) System.arraycopy(rx, 3, res, 1, copy);
                return true;
            } else if (boot == (byte)0xF4) {                 // erro
                res[0] = (byte)0xF4;
                res[1] = (len >= 2) ? rx[3] : 0;
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void wireless_TcpClose (){
        try { if (tcpSocket  != null) tcpSocket.close();   } catch (Exception ignore) {}
        try { if (tcpServer != null) tcpServer.close(); } catch (Exception ignore) {}
        tcpSocket = null; tcpServer = null; netIn = null; netOut = null;
    }

    public int wirelessGetReaderVersion(int hScanner,short[] wHardVer,short[] wSoftVer){
        int res = _OK;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];

        try {
            cmd[0] = 2;
            cmd[1] = 2;
            cmd[2] = 0;
//        WirelessRunner();

            if (!wirelessTcpPacket(hScanner, cmd, ret)) {
                return _net_error;
            }
            ;
            if (ret[0] == -12) {
                return ret[1];
            }


            wHardVer[0] = (short) (ret[2] * 256 + ret[3]);
            wSoftVer[0] = (short) (ret[4] * 256 + ret[5]);


            res = _OK;
        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Falha ao obter versao do Leitor");
            res = -1;
        }
        return res;

    }
    enum Transport { TCP, UDP }

    private boolean Net_PacketX(int hScanner, byte[] cmd, byte[] res) {
        if (transport == Transport.TCP) {
            return wirelessTcpPacket(hScanner, cmd, res);
        } else {
            return Net_Packet(hScanner, cmd, res);
        }
    }

}
