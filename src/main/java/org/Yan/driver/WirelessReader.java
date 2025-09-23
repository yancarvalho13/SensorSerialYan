package org.Yan.driver;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class WirelessReader {
    private InputStream inputStream;
    private OutputStream outputStream;
    private Socket socket;
    public static final int _OK          = 0;
    public static final int _comm_error  = 131;
    public static final int _net_error   = 134;
    private static final int MAX_PACKET_LEN = 262;
    private static final int TIMEOUT_MS     = 2000; // TimeOut do legado



    public WirelessReader(Socket socket) {
        this.socket = socket;
        try {
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    private boolean wirelessTcpPacket(int hScanner, byte[] cmd, byte[] res) {
        try {
            if (inputStream == null || outputStream == null) return false;

            // montar TX: [0x40][len=cmd[0]][payload...][checksum]
            byte[] send = new byte[(cmd[0] & 0xFF) + 2];
            byte sum = 0x40;
            send[0] = 0x40;
            for (int i = 0; i < (cmd[0] & 0xFF); i++) { send[i+1] = cmd[i]; sum += send[i+1]; }
            sum = (byte)(~sum + 1);
            send[send.length - 1] = sum;

            // envia
            outputStream.write(send);
            outputStream.flush();

            // lê cabeçalho de 4 bytes: [F0/F4][len][cmdEco][...]
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            byte[] head4 = readExact(inputStream, 4, deadline);     // você já tem readExact(...)
            byte boot = head4[0];
            int  len  = head4[1] & 0xFF;                      // (cmd + dados)
            int  rest = Math.max(0, len - 2);                 // bytes restantes após [len][cmdEco]
            byte[] body = (rest > 0) ? readExact(inputStream, rest, deadline) : new byte[0];

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
            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
        }
        return tags;
    }

    public int Net_AutoMode(int hScanner, int Mode, int Address) {
        int res = 0;
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];
        try {
            if (Address == 0) { cmd[0]=3; cmd[1]=15; cmd[2]=(byte)Mode; cmd[3]=0; }
            else              { cmd[0]=4; cmd[1]=15; cmd[2]=(byte)Address; cmd[3]=(byte)Mode; cmd[4]=0; }

            if (!wirelessTcpPacket(hScanner, cmd, ret)) return _net_error;
            if (ret[0] == (byte)0xF4) return (ret[1] & 0xFF); // erro
            return _OK;
        } catch (Exception e) {
            e.printStackTrace(); res = -1;
        }
        return res;
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

            if (!wirelessTcpPacket(hScanner, put, ret)) return _net_error;
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

            if (!wirelessTcpPacket(hScanner, put, ret)) return _net_error;
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
}
