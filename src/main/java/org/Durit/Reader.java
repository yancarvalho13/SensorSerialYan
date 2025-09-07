package org.Durit;

import com.fazecast.jSerialComm.SerialPort;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

public class Reader {

    // ===== Constantes do legado =====
    public static final int _OK          = 0;
    public static final int _comm_error  = 131;
    public static final int _net_error   = 134;
    private final String _ETIQUETA = "E280691500004021E743F052";
    private HashMap<String, String> bancoDeTags = new HashMap<>();

    private static final int MAX_PACKET_LEN = 262;
    private static final int TIMEOUT_MS     = 2000; // TimeOut do legado

    // ===== Estado serial (jSerialComm) =====
    private SerialPort port;
    private InputStream in;
    private OutputStream out;

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
        pararLeitura();

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
}
