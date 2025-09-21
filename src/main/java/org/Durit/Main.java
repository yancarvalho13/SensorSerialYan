package org.Durit;

import com.fazecast.jSerialComm.SerialPort;
import org.Durit.driver.Reader;

import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        SerialPortRunner();


    }

    private static void  networkRunner(){

    }

    private static void SerialPortRunner() {
       /* SerialPort[] portas;
        portas = SerialPort.getCommPorts();
        SerialPort sensor = portas[1];
        */
        int baud = 115200;
        int hScanner = 0;
        int[] scanner = new int[1];
        int Address  = 0; // você está usando RS232 com Address=0

        Reader r = new Reader();
        try {
       /* if (!r.open(sensor, baud)) {
            System.err.println("Falha ao abrir porta: " +sensor.getDescriptivePortName() );
            return;
        }

            int res;
            int ConnectMode = 1; // 1 = RS232
          // ---- Ler BasicParam (até 5 tentativas) ----
            byte[] gBasicParam = new byte[32];
           for (int i = 0; i < 5; i++) {
              res = r.ReadBasicParam(hScanner, gBasicParam, Address);
                if (res == Reader._OK) break;
                if (i == 4) {
                    System.err.println("Connect Reader Fail!(BasicParam)");
                    return;
                }
        }
            System.out.println("BasicParam OK");

            // ---- Ler AutoParam (até 5 tentativas) ----
            byte[] gAutoParam = new byte[32];
            for (int i = 0; i < 5; i++) {
                res = r.ReadAutoParam(hScanner, gAutoParam, Address);
                if (res == Reader._OK) {
                    // host port (mantendo sem “inventar”: se quiser o cálculo direto: ((b21&0xFF)<<8)|(b22&0xFF))
                    int b21 = gAutoParam[21] & 0xFF;
                    int b22 = gAutoParam[22] & 0xFF;
                    int iHostPort = (b21 << 8) | b22;
                    System.out.println("AutoParam OK, hostPort=" + iHostPort);
                    break;
                }
                if (i == 4) {
                    System.err.println("Connect Reader Fail!(AutoParam)");
                    return;
                }
            }

            short[] HandVer = new short[1];
            short[] SoftVer = new short[1];
            res = r.getReaderVersion(hScanner, HandVer, SoftVer, 0);
            System.out.println("HW=" + HandVer[0] + "  SW=" + SoftVer[0]);
            byte[] ip = new byte[4];
            int[] port = new int[1];
            byte[] mask = new byte[4];
            byte[] gateway = new byte[4];
       r.getReaderNet(hScanner,ip, port, mask, gateway); */


        short[] HandVer = new short[1];
        short[] SoftVer = new short[1];
            byte[] ip = new byte[4];
            int[] port = new int[1];
            byte[] mask = new byte[4];
            byte[] gateway = new byte[4];
            r.Net_ConnectScanner(scanner, "192.168.0.100",1969,"192.168.0.10",4444);
            r.Network_GetReaderVersion(hScanner, HandVer, SoftVer);
            System.out.println("HW=" + HandVer[0] + "  SW=" + SoftVer[0]);



//            ---- Start AutoMode (RS232) ----
//
            Set<String> tags = r.Net_getDistinctTags();
        System.out.println(tags);



        } finally {
            r.close();

        }
    }
}
