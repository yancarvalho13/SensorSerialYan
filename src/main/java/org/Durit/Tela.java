package org.Durit;

import com.fazecast.jSerialComm.SerialPort;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Tela {

    public void onReady(){
    }

    private HashMap<String, String> iniciarLeitura() {
        SerialPort[] portas;
        portas = SerialPort.getCommPorts();
        SerialPort sensor = portas[1];
        int baud = 115200;
        int hScanner = 0;
        int Address  = 0; // você está usando RS232 com Address=0
        HashMap<String, String> listaDeTags = new HashMap<>();

        Reader r = new Reader();
        if (!r.open(sensor, baud)) {
            System.err.println("Falha ao abrir porta: " +sensor.getDescriptivePortName() );
            return null;
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
            listaDeTags = r.getBancoDeTags();
            return listaDeTags;
        } finally {
            r.close();
        }

    }

    private void iniciarTela() {
        JFrame jFrame = new JFrame("Exemplo de Tabela");
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.setSize(1024, 720);
        JButton botaoLeitura = new JButton("Ler tags");

        String[] colunas = {"Modelo","Código da Tag", "Setor"};
        DefaultTableModel dafaultModel = new DefaultTableModel(colunas,0);
        JTable tabela = new JTable(dafaultModel);
        botaoLeitura.addActionListener(e -> {
            for(Map.Entry<String, String> entry : iniciarLeitura().entrySet()){
                dafaultModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
            }
            tabela.setModel(dafaultModel);
        });

        JScrollPane painelTabela = new JScrollPane(tabela);

        JPanel principal = new JPanel(new BorderLayout());

        principal.add(painelTabela, BorderLayout.CENTER);


        Object[] rows = new Object[tabela.getRowCount()];



        principal.add(botaoLeitura, BorderLayout.SOUTH);

        jFrame.add(principal);

        jFrame.setVisible(true);
    }

}
