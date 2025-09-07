package org.Durit;

import com.fazecast.jSerialComm.SerialPort;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
       SerialPort[] portas;
       portas = SerialPort.getCommPorts();
       SerialPort sensor = portas[1];
        int baud = 115200;
        int hScanner = 0;
        int Address  = 0; // você está usando RS232 com Address=0

        Reader r = new Reader();
        if (!r.open(sensor, baud)) {
            System.err.println("Falha ao abrir porta: " +sensor.getDescriptivePortName() );
            return;
        }
        try {
            int res;
            int ConnectMode = 1; // 1 = RS232
//
//            // ---- Ler BasicParam (até 5 tentativas) ----
//            byte[] gBasicParam = new byte[32];
//            for (int i = 0; i < 5; i++) {
//                res = r.ReadBasicParam(hScanner, gBasicParam, Address);
//                if (res == Reader._OK) break;
//                if (i == 4) {
//                    System.err.println("Connect Reader Fail!(BasicParam)");
//                    return;
//                }
//            }
//            System.out.println("BasicParam OK");

            // ---- Ler AutoParam (até 5 tentativas) ----
//            byte[] gAutoParam = new byte[32];
//            for (int i = 0; i < 5; i++) {
//                res = r.ReadAutoParam(hScanner, gAutoParam, Address);
//                if (res == Reader._OK) {
//                    // host port (mantendo sem “inventar”: se quiser o cálculo direto: ((b21&0xFF)<<8)|(b22&0xFF))
//                    int b21 = gAutoParam[21] & 0xFF;
//                    int b22 = gAutoParam[22] & 0xFF;
//                    int iHostPort = (b21 << 8) | b22;
//                    System.out.println("AutoParam OK, hostPort=" + iHostPort);
//                    break;
//                }
//                if (i == 4) {
//                    System.err.println("Connect Reader Fail!(AutoParam)");
//                    return;
//                }
//            }


            // ---- Start AutoMode (RS232) ----
            r.iniciarLeituraManual();
            r.imprimirTags();

        } finally {
            r.close();

        }
    }
}
