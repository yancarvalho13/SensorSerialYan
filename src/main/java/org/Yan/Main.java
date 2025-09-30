package org.Yan;

import com.fazecast.jSerialComm.SerialPort;
import org.Yan.driver.Reader;
import org.Yan.driver.WirelessReader;

import java.net.Socket;
import java.util.*;

public class Main {
    public static void main(String[] args) {

            ProxyRunner();
//        serial();


    }

    private static void ProxyRunner(){
        try(Socket socket = new Socket("192.168.2.1", 6000 )){


            var tagList = getTagList();
            System.out.println(tagList);
            WirelessReader r = new WirelessReader(socket);
            short[] hv = new short[1], sv = new short[1];
            r.wirelessGetReaderVersion(0, hv, sv);   // internamente chame Net_PacketTcp
            System.out.println("HW=" + hv[0] + "  SW=" + sv[0]);
//            int result = r.setReadInterval(0,1);
//            if (result == WirelessReader._OK) {
//                System.out.println("Intervalo de leitura configurado para 100ms");
//            } else {
//                System.out.println("Falha ao configurar intervalo de leitura: " + result);
//            }
            r.printParams(r.readParams(0));
                var star = System.currentTimeMillis();
                var list = r.Net_getDistinctTagsBatchedMultithread(40);
            System.out.println(list);
            System.out.println(list.size());
            var end = System.currentTimeMillis();
            System.out.println("Tempo de leitura; " + star+end);
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public static Set<String> parseBtOutToSet(byte[] all) {
        LinkedHashSet<String> epcs = new LinkedHashSet<>();
        if (all == null || all.length == 0) return epcs;

        int pos = 0;
        while (pos < all.length) {
            int L = all[pos] & 0xFF;       // tamanho do EPC em WORDS (cada WORD = 2 bytes)
            if (L == 0) {                  // sentinela/fim
                pos++;
                continue;
            }

            int bytes = 1 + L * 2;         // [L][EPC...]
            if (L > 31 || pos + bytes > all.length) {
                // bloco inválido/incompleto => avança 1 e tenta resincronizar
                pos++;
                continue;
            }

            // Monta o EPC em hex
            StringBuilder sb = new StringBuilder(L * 4);
            for (int i = 0; i < L * 2; i++) {
                sb.append(String.format("%02X", all[pos + 1 + i]));
            }
            String epc = sb.toString();

            // Filtros de sanidade (ajuste as regras conforme seu cenário):
            // - começa com "E2" (Alien/Impinj UHF comum)
            // - sem bloco gigantesco de zeros
            // - comprimento típico 24 hex (12 bytes = L=6) — mantenha se for seu caso
            boolean looksZeroish = epc.contains("000000000000"); // lixo comum que você relatou
            boolean prefixOk     = epc.startsWith("E2");
            boolean lenOk        = (epc.length() == 24);         // ajuste se usar EPCs maiores

            if (prefixOk && lenOk && !looksZeroish) {
                epcs.add(epc);
            }

            pos += bytes; // avança para o próximo grupo
        }
        return epcs;
    }

    private static Set<String> serial(){
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
            var tags = r.getDistinctTags();
            System.out.println(tags);
            return  tags;
        } finally {
            r.close();
        }
    }
    private static List<String> getTagList(){
        List<String> epcs = List.of(
                "E2801191A5040074C31235EB",
                "E2801191A5040074C31235EC",
                "E2801191A5040074C31235ED",
                "E2801191A5040074C31235EE",
                "E2801191A5040074C31237E8",
                "E2801191A5040074C31237E9",
                "E2801191A5040074C31237E6",
                "E2801191A5040074C31237E7",
                "E2801191A5040074C31237E0",
                "E2801191A5040074C31237E1",
                "E2801191A5040074C31237E4",
                "E2801191A5040074C31237E5",
                "E2801191A5040074C31237E2",
                "E2801191A5040074C31237E3",
                "E2801191A5040074C31238EE",
                "E2801191A5040074C31238EF",
                "E2801191A5040074C31236EA",
                "E2801191A5040074C31234EC",
                "E2801191A5040074C31236EB",
                "E2801191A5040074C31234EA",
                "E2801191A5040074C31234EB",
                "E2801191A5040074C31238EC",
                "E2801191A5040074C31236EE",
                "E2801191A5040074C31238ED",
                "E2801191A5040074C31236EF",
                "E2801191A5040074C31234EE",
                "E2801191A5040074C31234EF",
                "E2801191A5040074C31236ED",
                "E20806E2801191A5040074C3",
                "E280112106E2801191A50400",
                "E22006E2801191A5040074C3",
                "E2801191A5040074C31255E0",
                "E22206E2801191A5040074C3",
                "E2801191A5040074C31236E9",
                "E2801191A5040074C31236E7",
                "E2801191A5040074C31234E9",
                "E2801191A5040074C31236E8",
                "E2801191A5040074C31236E1",
                "E2801191A5040074C31234E4",
                "E2801191A5040074C31236E2",
                "E2801191A5040074C31236E0",
                "E2801191A5040074C31234E2",
                "E2801191A5040074C31236E5",
                "E2801191A5040074C31234E7",
                "E2801191A5040074C31234E8",
                "E2801191A5040074C31236E6",
                "E2801191A5040074C31234E5",
                "E2801191A5040074C31236E3",
                "E2801191A5040074C31234E6",
                "E2801191A5040074C31236E4"
        );
        return epcs;
    }





}
