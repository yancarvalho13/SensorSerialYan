package org.Yan.driver;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WirelessReader {
    private InputStream inputStream;
    private OutputStream outputStream;
    private Socket socket;
    public static final int _OK          = 0;
    public static final int _comm_error  = 131;
    public static final int _net_error   = 134;
    private static final int MAX_PACKET_LEN = 262;
    private static final int TIMEOUT_MS     = 200000; // TimeOut do legado
    private List<Byte> msg = new ArrayList<>();
    private final ByteArrayOutputStream btOutGlobal = new ByteArrayOutputStream(4096);

    public WirelessReader(Socket socket) {
        this.socket = socket;
        try {
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    public synchronized void clearBtOut() {
        btOutGlobal.reset();
    }

    /** Retorna uma CÓPIA dos bytes concatenados (thread-safe) */
    public synchronized byte[] getBtOut() {
        return btOutGlobal.toByteArray();
    }

    private static int contarGrupos(byte[] buffer, int pos, int len) {
        int end = pos + len;
        int count = 0;
        while (pos < end) {
            int L = buffer[pos] & 0xFF;
            if (L == 0) break;
            int bytes = 1 + L * 2;
            if (pos + bytes > end) break; // incompleto
            pos += bytes;
            count++;
        }
        return count;
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
            byte[] head4 = readExact(inputStream, 4, deadline);
            System.out.println(head4[0]+", "+head4[1] +", "+ head4[2]+", "+head4[3]);// você já tem readExact(...)
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
        Set<String> tags = new HashSet<>();

        try {
            // garante que não estamos em push por rede enquanto fazemos polling
            Net_AutoMode(hScanner, 0, Address);

            // parâmetros da leitura (EPC, sem máscara)
            final int mem = 1, ptr = 0, len = 0;
            byte[] mask = new byte[0];

            // buffers e metadados (LEGADO)
            byte[] firstBuf = new byte[4094];
            int[]  nCounter = new int[1];   // total de grupos reportados pelo leitor
            int[]  IDlen    = new int[1];   // bytes válidos copiados da 1ª página (<=8 grupos)

            // 1) primeira página via EEh (LEGADO)
            int r = Net_EPC1G2_ListTagID(hScanner, mem, ptr, len, mask, firstBuf, nCounter, IDlen);
            if (r != _OK) return tags;

            // parse dos grupos retornados na 1ª página
            int got = parseListBuffer(firstBuf, IDlen[0], tags);

            // 2) se houver mais que 8, paginação via EDh (LEGADO)
            int total = nCounter[0];              // total de grupos (tags) reportados
            int start = got;                      // índice de grupo a partir do qual vamos buscar
            int left  = Math.max(0, total - got); // quantos grupos faltam

            final int ED_PAGE = 10; // EDh retorna até 10 grupos por chamada

            while (left > 0) {
                int fetch = Math.min(ED_PAGE, left);
                byte[] pageBuf = new byte[1024];
                int[]  pageLen = new int[1];

                r = Net_EPC1G2_GetIDList(hScanner, pageBuf, start, fetch, pageLen);
                if (r != _OK) break;

                parseListBuffer(pageBuf, pageLen[0], tags);

                start += fetch;   // avança por "grupos"
                left  -= fetch;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return tags;
    }

    /** Lê uma sequência de grupos no formato [L][EPC L*2 bytes]...[L=0/EOF] e adiciona ao Set */
    private static int parseListBuffer(byte[] buf, int validLen, Set<String> out) {
        int pos = 0, groups = 0;
        while (pos < validLen) {
            int L = buf[pos] & 0xFF;
            if (L == 0) break;
            int bytes = 1 + L * 2;
            if (pos + bytes > validLen) break; // incompleto, para

            // monta EPC em hex
            StringBuilder epc = new StringBuilder(L * 4);
            for (int i = 0; i < L * 2; i++) {
                epc.append(String.format("%02X", buf[pos + 1 + i]));
            }
            String tag = epc.toString();

            // filtros básicos (iguais à sua lógica)
            if (!tag.isEmpty() && !tag.contains("000000000000")) {
                // se quiser o mesmo crivo estrito de 24 hex e começando com E2, habilite:
                // if (tag.length() == 24 && tag.startsWith("E2")) {
                //     out.add(tag);
                // } else {
                //     System.out.println("Tag quebrada: " + tag);
                // }
                out.add(tag);
            }

            pos += bytes;
            groups++;
        }
        return groups;
    }
    public List<Byte> getMsg() {
        return msg;
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
    private  int Net_EPC1G2_ListTagID(int hScanner, int mem, int ptr, int len, byte[] mask, byte[] btID, int[] nCounter, int[] IDlen) {
        int res = _OK;
        byte[] put = new byte[MAX_PACKET_LEN];
        byte[] get = new byte[MAX_PACKET_LEN];

        try {
            int m;
            if (len == 0) {
                m = 0;
            } else {
                m = len / 8;
                if (len % 8 != 0) {
                    ++m;
                }
            }

            put[0] = (byte)(m + 6);
            put[1] = -18;
            put[2] = (byte)mem;
            put[3] = (byte)(ptr >> 8);
            put[4] = (byte)ptr;
            put[5] = (byte)len;

            int i;
            for(i = 0; i < m; ++i) {
                put[6 + i] = mask[i];
            }

            put[6 + i] = 0;
            if (!wirelessTcpPacket(hScanner, put, get)) {
                return _net_error;
            }

            if (get[0] == -12) {
                return get[1];
            }

            nCounter[0] = get[1];
            int last = 2;
            if (nCounter[0] <= 8) {
                for(int var19 = 0; var19 < nCounter[0]; ++var19) {
                    int next = get[last] * 2 + 1;
                    System.arraycopy(get, last, btID, last - 2, next);
                    last += next;
                }
            } else {
                for(int var18 = 0; var18 < 8; ++var18) {
                    int next = get[last] * 2 + 1;
                    System.arraycopy(get, last, btID, last - 2, next);
                    last += next;
                }
            }

            IDlen[0] = last - 2;
            res = _OK;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception of Net_EPC1G2_ListTagID:\n");
            res = -1;
        }

        return res;
    }

    private  int Net_EPC1G2_GetIDList(int hScanner, byte[] btID, int stNum, int nCounter, int[] IDlen) {
        int res = _OK;
        byte[] put = new byte[MAX_PACKET_LEN];
        byte[] get = new byte[MAX_PACKET_LEN];

        try {
            put[0] = 4;
            put[1] = -19;
            put[2] = (byte)stNum;
            put[3] = (byte)nCounter;
            put[4] = 0;
            if (!wirelessTcpPacket(hScanner, put, get)) {
                return _net_error;
            }

            if (get[0] == -12) {
                return get[1];
            }

            int last = 2;

            for(int i = 0; i < nCounter; ++i) {
                int next = get[last] * 2 + 1;
                System.arraycopy(get, last, btID, last - 2, next);
                last += next;
            }

            IDlen[0] = last - 2;
            res = _OK;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception of Net_EPC1G2_GetIDList:\n");
            res = -1;
        }

        return res;
    }

    public  int Net_EPC1G2_ReadLabelID(int hScanner, int mem, int ptr, int len, byte[] mask, byte[] IDBuffer, int[] nCounter) {
        int res = _OK;

        try {
            int i = 0;
            int j = 0;
            int k = 0;
            int[] l = new int[2];
            int[] IDlen = new int[2];
            byte[] lpbuffer = new byte[1024];
            nCounter[0] = 0;
            res = Net_EPC1G2_ListTagID(hScanner, mem, ptr, len, mask, IDBuffer, nCounter, IDlen);
            if (res != _OK) {
                return res;
            }

            if (nCounter[0] > 8) {
                j = nCounter[0] / 8;
                k = nCounter[0] - j * 8;

                for(int e = 1; e < j; ++e) {
                    res = Net_EPC1G2_GetIDList(hScanner, lpbuffer, e * 8, 8, l);
                    if (res != _OK) {
                        return res;
                    }

                    IDlen[0] += l[0];
                }

                if (k != 0) {
                    res = Net_EPC1G2_GetIDList(hScanner, lpbuffer, j * 8, k, l);
                    if (res != _OK) {
                        return res;
                    }
                }
            }

            res = _OK;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception of Net_EPC1G2_ReadLabelID:\n");
            res = -1;
        }

        return res;
    }
/**
 * Configura o intervalo de leitura no modo automático
 * @param hScanner identificador do scanner (geralmente 0)
 * @param intervalValue valor do intervalo:
 *                     0 = 10ms
 *                     1 = 20ms
 *                     2 = 30ms (padrão)
 *                     3 = 50ms
 *                     4 = 100ms
 * @return código de resultado (0 = sucesso)
 */
public int setReadInterval(int hScanner, int intervalValue) {
    if (intervalValue < 0 || intervalValue > 4) {
        System.out.println("Valor de intervalo inválido. Use: 0=10ms, 1=20ms, 2=30ms, 3=50ms, 3=100ms");
        return -1;
    }
    
    int Address = 0;  // Endereço padrão
    byte[] autoParams = new byte[32];
    
    // 1. Primeiro lemos os parâmetros atuais
    byte[] cmd = new byte[MAX_PACKET_LEN];
    byte[] ret = new byte[MAX_PACKET_LEN];
    
    // Comando para ler AutoParam
    cmd[0] = 2;
    cmd[1] = 20;  // Comando 0x14 (20) = ReadAutoParam
    cmd[2] = 0;
    
    if (!wirelessTcpPacket(hScanner, cmd, ret)) {
        System.out.println("Falha ao ler parâmetros automáticos");
        return _net_error;
    }
    
    if (ret[0] == (byte)0xF4) {
        System.out.println("Erro ao ler parâmetros: " + (ret[1] & 0xFF));
        return (ret[1] & 0xFF);
    }
    
    // Copiar os parâmetros recebidos
    System.arraycopy(ret, 1, autoParams, 0, 32);
    
    // 2. Modificar o byte 4 (índice 3) que controla o intervalo de leitura
    autoParams[3] = (byte)intervalValue;
    
    // 3. Escrever os parâmetros de volta
    cmd = new byte[MAX_PACKET_LEN];
    ret = new byte[MAX_PACKET_LEN];
    
    cmd[0] = (byte)(2 + 32);  // Length = 2 + 32 bytes de parâmetros
    cmd[1] = 19;  // Comando 0x13 (19) = WriteAutoParam
    System.arraycopy(autoParams, 0, cmd, 2, 32);
    cmd[2 + 32] = 0;
    
    if (!wirelessTcpPacket(hScanner, cmd, ret)) {
        System.out.println("Falha ao escrever parâmetros automáticos");
        return _net_error;
    }
    
    if (ret[0] == (byte)0xF4) {
        System.out.println("Erro ao escrever parâmetros: " + (ret[1] & 0xFF));
        return (ret[1] & 0xFF);
    }
    
    System.out.println("Intervalo de leitura configurado com sucesso para: " + 
                      (intervalValue == 0 ? "10ms" : 
                       intervalValue == 1 ? "20ms" : 
                       intervalValue == 2 ? "30ms" : 
                       intervalValue == 3 ? "50ms" : "100ms"));
    
    return _OK;
}

    /**
     * Método que lê múltiplos lotes de tags e usa multithreading para processamento
     */
    public Set<String> Net_getDistinctTagsBatchedMultithread(int numBatches) {
        final int hScanner = 0, Address = 0;
        final ConcurrentHashMap<String, Boolean> allTags = new ConcurrentHashMap<>();
        final ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() - 1)
        );

        try {
            // Garantir modo correto
            Net_AutoMode(hScanner, 0, Address);

            // Ler em vários lotes
            for (int batch = 0; batch < numBatches; batch++) {
                byte[] IDBuffer = new byte[4096];
                int[] nCounter = new int[1];

                // Limpar o buffer
                Arrays.fill(IDBuffer, (byte)0);

                // Leitura
                int r = Net_EPC1G2_ReadLabelID(hScanner, 1, 0, 0, new byte[0], IDBuffer, nCounter);

                if (r == _OK && nCounter[0] > 0) {
                    System.out.println("Lote " + (batch + 1) + ": Detectadas " + nCounter[0] + " tags");

                    // Criar uma cópia do buffer para processamento assíncrono
                    final byte[] bufferCopy = Arrays.copyOf(IDBuffer, IDBuffer.length);
                    final int tagCount = nCounter[0];

                    // Submeter o lote para processamento em segundo plano
                    executor.submit(() -> {
                        // Processar as tags deste lote
                        processTagsToMap(bufferCopy, tagCount, allTags);
                    });

                    // Esperar um pouco entre lotes para evitar sobrecarregar o dispositivo
                    Thread.sleep(500);
                } else {
                    // Se não encontrou tags neste lote, não precisa continuar
                    if (batch > 0) break;
                }
            }

            // Aguardar todas as threads concluírem
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }
        }

        return new HashSet<>(allTags.keySet());
    }

    /**
     * Processa as tags de um buffer e adiciona ao mapa concorrente
     */
    private void processTagsToMap(byte[] buffer, int numTags, ConcurrentHashMap<String, Boolean> tagsMap) {
        int idx = 0;
        int tagsProcessed = 0;

        while (idx < buffer.length && tagsProcessed < numTags) {
            if (buffer[idx] == 0) break;

            int L = buffer[idx] & 0xFF;

            if (L < 1 || L > 31 || idx + 1 + L * 2 > buffer.length) {
                idx++;
                continue;
            }

            StringBuilder epc = new StringBuilder(L * 4);
            for (int k = 0; k < L * 2; k++) {
                epc.append(String.format("%02X", buffer[idx + 1  + k]));
            }

            char a = 'E';
            char b = '2';
            String tag = epc.toString();
            System.out.println("Tag: "+ tag);
            if (!tag.isEmpty() && !tag.contains("000000000000")) {

                    tagsMap.put(tag, true);

            }

            idx += 1 + L * 2;
            tagsProcessed++;
        }
    }


    /**
     * Lê o bloco de 32 bytes de parâmetros básicos de operação do leitor.
     *
     * @param hScanner O identificador do scanner (geralmente 0).
     * @return Um array de 32 bytes com os parâmetros em caso de sucesso, ou null em caso de falha.
     */
    public byte[] readParams(int hScanner) {
        System.out.println("Iniciando leitura dos parâmetros básicos do leitor...");
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];

        try {
            // Montando o comando 06H (Read Param)
            // Baseado no manual e no seu código existente: [comprimento], [comando], [terminador nulo]
            cmd[0] = 2;       // Comprimento do payload (comando + terminador)
            cmd[1] = 0x06;    // Comando 06H: Read Param
            cmd[2] = 0;

            if (!wirelessTcpPacket(hScanner, cmd, ret)) {
                System.out.println("Falha na comunicação ao tentar ler os parâmetros.");
                return null; // Retorna nulo em caso de erro de rede/comunicação
            }

            // Verifica se o dispositivo retornou um código de erro
            if (ret[0] == (byte)0xF4) {
                System.out.println("O leitor retornou um erro ao ler parâmetros: " + (ret[1] & 0xFF));
                return null; // Retorna nulo em caso de erro do dispositivo
            }

            // Se chegamos aqui, a operação foi um sucesso (ret[0] == 0xF0)
            // O manual diz que a resposta contém 32 bytes de dados.
            byte[] params = new byte[32];

            // Os dados começam no segundo byte da resposta (`ret[1]`),
            // pois o primeiro é o status `F0H`.
            System.arraycopy(ret, 1, params, 0, 32);

            System.out.println("Parâmetros lidos com sucesso.");
            return params;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Imprime os parâmetros de operação de forma legível, com base no array de 32 bytes.
     * @param params O array de 32 bytes retornado pela função readParams.
     */
    public void printParams(byte[] params) {
        if (params == null || params.length < 32) {
            System.out.println("Array de parâmetros inválido ou nulo.");
            return;
        }

        System.out.println("--- Parâmetros Atuais do Leitor ---");
        // Usamos '& 0xFF' para garantir que o valor do byte seja exibido como um número de 0 a 255.

        // O manual usa base 1, nosso array usa base 0. Ex: Byte 1 é índice 0.
        System.out.println("Byte  1 (Índice  0) - BaudRate: " + (params[0] & 0xFF));
        System.out.println("Byte  2 (Índice  1) - Potência de Transmissão (0-63): " + (params[1] & 0xFF));
        System.out.println("Byte  6 (Índice  5) - Modo de Trabalho (0=Auto, 1=Comando): " + (params[5] & 0xFF));
        System.out.println("Byte  7 (Índice  6) - Endereço RS485: " + (params[6] & 0xFF));
        System.out.println("Byte  8 (Índice  7) - Máx. Tags por Leitura: " + (params[7] & 0xFF));
        System.out.println("Byte  9 (Índice  8) - Tipo de Tag (4=G2): " + (params[8] & 0xFF));
        System.out.println("Byte 10 (Índice 9) - Duração da leitura da tag: " + (params[9 & 0xFF]));
        System.out.println("Byte 11 (Índice 10) - Read Times (M): " + (params[10] & 0xFF));
        System.out.println("Byte 12 (Índice 11) - Buzzer Ativado (1=Sim, 0=Não): " + (params[11] & 0xFF));

        // Exemplo de como ler um parâmetro de múltiplos bytes (IP)
        String ipAddress = (params[12] & 0xFF) + "." + (params[13] & 0xFF) + "." + (params[14] & 0xFF) + "." + (params[15] & 0xFF);
        System.out.println("Bytes 13-16 (Índices 12-15) - Endereço IP: " + ipAddress);

        System.out.println("------------------------------------");
    }
}
