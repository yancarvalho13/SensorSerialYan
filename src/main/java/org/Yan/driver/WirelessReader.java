    package org.Yan.driver;

    import org.Yan.exceptions.domainExceptions.InvalidResponseReaderException;
    import org.Yan.exceptions.domainExceptions.NetWorkErrorException;

    import java.io.*;
    import java.net.Socket;
    import java.util.*;
    import java.util.concurrent.atomic.AtomicInteger;

    /**
     * Driver para o sensor **Fonkan FI-801**, desenvolvido por **Yan da Silva de Carvalho** —
     * Engenheiro de Software e Arquiteto de Soluções.
     * <p>
     * Contato: <a href="mailto:yansilva303@gmail.com">yansilva303@gmail.com</a>
     * <p>
     * Este driver implementa a comunicação TCP entre o host e o microcontrolador do sensor,
     * através do envio e recebimento de pacotes binários (arrays de bytes).
     * <p>
     * Cada operação de leitura, escrita ou controle é realizada via socket,
     * obedecendo ao protocolo definido no manual de desenvolvimento do fabricante.
     * <p>
     * <b>Resumo técnico:</b>
     * <ul>
     *   <li>Protocolo: TCP/IP</li>
     *   <li>Camada de aplicação: frames binários estruturados</li>
     *   <li>Codificação: Little-Endian (salvo exceções indicadas)</li>
     *   <li>Timeout: 200 000 ms (compatível com o firmware legado)</li>
     * </ul>
     *
     * <p><b>Observação:</b> para informações detalhadas sobre os comandos,
     * estruturas de pacotes e significados de códigos de erro,
     * consulte o manual oficial do fabricante do sensor Fonkan FI-801.
     */
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
        private final AtomicInteger maxDistinct = new AtomicInteger(0);

        private final Set<String> allDistinct = new HashSet<>();
        private final ByteArrayOutputStream btOutGlobal = new ByteArrayOutputStream(4096);
        private volatile boolean running = true;
        private final int intervalMs = 50;    // intervalo entre ciclos


        /**
         * Construtor que inicializa o leitor a partir de um {@link Socket} já conectado.
         *
         * @param socket socket TCP conectado ao microcontrolador do sensor.
         * @throws RuntimeException caso ocorra falha ao obter os streams de entrada ou saída.
         */
        public WirelessReader(Socket socket) {
            this.socket = socket;
            try {
                this.inputStream = socket.getInputStream();
                this.outputStream = socket.getOutputStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }




        /**
         *
         * @param hScanner
         * hScanner represente o estado do sensor, passamos int[] hScanner = new int[1]
         * -> dessa forma representamos que o sensor esta conectado, esse metodo e assinado em qualquer comando
         * passado para o sensor;
         * @param wHardVer
         * Aqui é onde recebemos e armazenamos a versao do Hardware do sensor, os valores sao devolvidos em WORDS ->
         * Words sao representados por 16 bits, logo passamos um byte[] wHardVer = new byte[2];
         * @param wSoftVer
         * Aqui é onde recebemos e armazenamos a versao do Software do sensor, os valores sao devolvidos em WORDS ->
         * WORDS são representados por 16 bits, logo passamos um byte[] wSoftVer = new byte[2];
         */
        public void wirelessGetReaderVersion(int hScanner,byte[] wHardVer,byte[] wSoftVer){
            int res = _OK;
            byte[] cmd = new byte[MAX_PACKET_LEN];
            byte[] ret = new byte[MAX_PACKET_LEN];

            try {
                cmd[0] = 2;
                cmd[1] = 2;
                cmd[2] = 0;
    //        WirelessRunner();

                if (!wirelessTcpPacket(hScanner, cmd, ret)) {
                    throw new NetWorkErrorException("Falha ao enviar pacote TCP");
                };

                if(ret[0] == (byte)0xF4){
                    throw new InvalidResponseReaderException("Falha ao obter Versão do Leitor");
                }

                System.arraycopy(ret, 2, wHardVer, 0, 2);
                System.arraycopy(ret, 4, wSoftVer, 0,2 );


            } catch (Exception e){
                e.printStackTrace();
            }

        }



        private boolean wirelessTcpPacket(int hScanner, byte[] cmd, byte[] res) {
            try {
                if (inputStream == null || outputStream == null) return false;

                /*Aqui montamos o pacote de envio para o sensor, criamos um array com 2 bytes
                no primeiro cmd[0] acomodamos o start 0x40 (padrão de todos os comandos) e deixamos
                um espaço vazio para o checksum (soma de todos os bytes do pacote) do final.
                 */
                byte[] send = new byte[(cmd[0] & 0xFF) + 2];
                byte sum = 0x40;
                send[0] = 0x40; // -> armazenamento do start 0x40
                for (int i = 0; i < (cmd[0] & 0xFF); i++) {
                    /*
                    aqui armazenamos os bytes do comando começando da pos 1 do pacote send
                    e fazemos a soma começando de 0x40 ate o ultimo byte do comando para obter
                    o checksum.
                     */
                    send[i+1] = cmd[i]; sum += send[i+1];
                }



                sum = (byte)(~sum + 1);
                send[send.length - 1] = sum;

                // envia
                outputStream.write(send);
                outputStream.flush();


                long deadline = System.currentTimeMillis() + TIMEOUT_MS;
                /*
                Aqui vamos ler os 4 bytes do inicio do pacote de resposta do sensor
                em head4[0] podemos obter 0xF0 (Sucesso) ou 0xF4 (erro);
                Em head4[1] temos o lenght da resposta, que inclui, o eco do comando head4[2]
                ,o dispositivo ecoa o comando para vc validar que aquela resposta e referente
                ao comando que vc enviou. E em seguida o payload head4[3] -> primeiro byte do
                payload;
                 */
                byte[] head4 = readExact(inputStream, 4, deadline);
                byte boot = head4[0];
                int  len  = head4[1] & 0xFF;
                /*
                Rest verifica e obtem o restante do payload que ainda falta ser lido
                a parti do len - 2 ( 1 é o cmdEco e 1 é o primeiro byte do payload)
                 */
                int  rest = Math.max(0, len - 2); // bytes restantes após [len][cmdEco]

                /*
                Faz a leitura dos bytes restantes do payload da resposta do sensor
                e armazena os valores em byte[] body
                 */
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


        public int Net_SetAntenna(int hScanner, int Antenna) {
            int res = _OK;
            byte[] put = new byte[MAX_PACKET_LEN];
            byte[] get = new byte[MAX_PACKET_LEN];

            try {
                put[0] = 3;
                put[1] = 10;
                put[2] = (byte)Antenna;
                put[3] = 0;
                if (!wirelessTcpPacket(hScanner, put, get)) {
                    return _net_error;
                }

                if (get[0] == -12) {
                    return get[1];
                }

                res = _OK;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception of Net_SetAntenna:\n");
                res = -1;
            }
            return res;
        }

        /**
         *
         * @param in
         * Stream de dados do pacote resposta do Sensor
         * @param restBytes
         * Quantidade de bytes que faltam para o final da leitura
         * @param deadlineMs
         * Tempo Maximo de leitura para timeout
         * @return
         * Um array de bytes com os dados enviados pelo sensor
         * @throws IOException
         * Exceção de Timeout por tempo de leitura
         */
        private static byte[] readExact(InputStream in, int restBytes, long deadlineMs) throws IOException {
            byte[] out = new byte[restBytes];
            int initialPosition = 0;
            while (initialPosition < restBytes) {
                if (System.currentTimeMillis() > deadlineMs)
                    throw new IOException("Serial read timeout (" + restBytes + " bytes)");
                /*
                readedByes, representa quantos bytes fora lidos na chamada de in.read.
                Logo in.read faz a leitura de todos os bytes no stream de dados, e copia para
                o array out.
                 */
                int readedBytes = in.read(out, initialPosition, restBytes - initialPosition);
                if (readedBytes < 0) throw new EOFException("Serial closed");
                if (readedBytes == 0) {
                    try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                    continue;
                }
                initialPosition += readedBytes;
            }
            return out;
        }

        public Set<String> Net_getDistinctTags(int hScanner) {
            try { Net_AutoMode(hScanner,0,0); Net_SetAntenna(hScanner, 1);} catch (Throwable ignore) {}
            var starttime = System.currentTimeMillis();

            // parâmetros fixos para leitura de EPC sem máscara
            final int mem = 1, ptr = 0, len = 0;
            final byte[] mask = new byte[0];

            // buffers de trabalho
            final byte[] firstBuf = new byte[7096]; // EEh 1ª página
            final int[]  nCounter = new int[1];     // total de grupos (tags) reportados
            final int[]  IDlen    = new int[1];     // bytes válidos da 1ª página

            // loop contínuo como no legado
            var finalTime = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < finalTime ) {
                try {
                    // -------- 1) Seleção de antena (igual ao legado) ----------


                    // -------- 2) Leitura paginada (EEh + EDh) ----------
                    int r;

                        // NET
                        r = Net_EPC1G2_ListTagID(hScanner, mem, ptr, len, mask, firstBuf, nCounter, IDlen);

                    if (r != _OK) {
                        sleepQuiet(intervalMs);
                        continue;
                    }

                    // parse da 1ª página
                    int got = parseListBuffer(firstBuf, IDlen[0], allDistinct);

                    // se houver mais, pagina com EDh
                    int total = nCounter[0]; // -> Total de tags lidas em ListTagId ; Ex: 50 tags
                    int start = got;
                    int left  = Math.max(0, total - got); // -> total - a primeira leitura (8)

                    final int ED_PAGE = 8; // EDh retorna até 10 grupos por chamada
                    while (left > 0) {
                        int fetch = Math.min(ED_PAGE, left);
                        byte[] pageBuf = new byte[1024];
                        int[]  pageLen = new int[1];

                            /*Aqui buscamos as tags baseado na posicao dela na memoria; 8 / 16 / 32 ->
                            cada pagina contem 8 tags; o numero total de paginas e pego em ListTagID;
                             */
                            r = Net_EPC1G2_GetIDList(hScanner, pageBuf, start, fetch, pageLen);

                        if (r != _OK) break;

                        parseListBuffer(pageBuf, pageLen[0], allDistinct);
                        start += fetch;
                        left  -= fetch;
                    }

                    // -------- 3) Console: imprime quando bater novo máximo ----------
                    int sz = allDistinct.size();
                    // só loga quando houver crescimento
                    int prev = maxDistinct.get();
                    if (sz > prev && maxDistinct.compareAndSet(prev, sz)) {
                        System.out.println("[MyTimer] Máximo histórico de tags únicas: " + sz);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    sleepQuiet(intervalMs);
                }
            }
            return allDistinct;
        }


        /**
         * Varre um buffer contendo uma lista de EPCs no formato
         * {@code [L][EPC(L*2 bytes)] [L][EPC] ...} e adiciona cada EPC decodificado
         * (em hexadecimal) ao conjunto de saída.
         * <p>
         * O protocolo empacota cada entrada iniciando por {@code L} (tamanho em WORDS, 1 WORD = 2 bytes),
         * seguido por {@code L*2} bytes do EPC. O método caminha sequencialmente pelo buffer até
         * consumir {@code usedLen} bytes, encontrar inconsistências ou um {@code L} inválido.
         *
         * <h3>Validações realizadas</h3>
         * <ul>
         *   <li>{@code L == 0} ou {@code L > 32} → encerra a varredura (sanity check).</li>
         *   <li>Buffer truncado: se não houver bytes suficientes para {@code 1 + L*2}, encerra.</li>
         * </ul>
         *
         * <h3>Complexidade</h3>
         * O(n), onde n é {@code usedLen}, com cópias lineares por grupo.
         *
         * @param buf     buffer de entrada com as entradas sequenciais no formato
         *                {@code [L][EPC(L*2)]...}.
         * @param usedLen quantidade de bytes válidos em {@code buf} a serem processados
         *                (a partir de {@code buf[0]}). Não são lidos bytes após {@code usedLen}.
         * @param out     conjunto de saída onde cada EPC será inserido em formato hexadecimal
         *                (uppercase/lowercase conforme a implementação de {@code bytes2HexString}).
         * @return quantidade de grupos (tags) processados e adicionados a {@code out}.
         *
         * @implNote Usa um buffer temporário fixo de 128 bytes para o EPC, suficiente para
         *           o máximo de 32 WORDS (64 bytes) de EPC ({@code 32 * 2}).
         * @see #bytes2HexString(byte[], int)
         */
        private static int parseListBuffer(byte[] buf, int usedLen, Set<String> out) {
            int i = 0, groups = 0;
            byte[] temp = new byte[128]; // EPC máx 32 words = 64 bytes → 128 cabe

            while (i < usedLen) {
                int L = buf[i] & 0xFF;   // comprimento em WORDS
                if (L == 0 || L > 32) break; // sanity check

                int epcBytes = L * 2;
                int need = 1 + epcBytes; // 1 (len) + EPC
                if (i + need > usedLen) break; // buffer truncado

                // copia o ID da tag começando após L
                System.arraycopy(buf, i + 1, temp, 0, epcBytes);

                // extrai a tag passando o array com o ID e o tamanho do ID
                String epcHex = bytes2HexString(temp, epcBytes);

                // adiciona a Tag ao conjunto (evita duplicados)
                out.add(epcHex);

                i += need;
                groups++;
            }
            return groups;
        }



        /**
         * Converte um array de bytes em uma string hexadecimal legível.
         * <p>
         * Cada byte é convertido em dois caracteres hexadecimais (0–F). Caso o valor
         * convertido possua apenas um dígito, é adicionado um '0' à esquerda para manter
         * o formato de dois dígitos por byte. O resultado final é retornado em letras
         * maiúsculas, sem espaços ou separadores.
         *
         * <h3>Exemplo:</h3>
         * <pre>{@code
         * byte[] data = { (byte)0xE2, (byte)0x80, 0x11 };
         * String hex = bytes2HexString(data, data.length);
         * // hex = "E28011"
         * }</pre>
         *
         * @param b      array de bytes de entrada.
         * @param count  quantidade de bytes válidos a converter, iniciando de {@code b[0]}.
         *               Caso {@code count} seja menor que {@code b.length}, os bytes excedentes
         *               são ignorados.
         * @return       representação hexadecimal em letras maiúsculas dos {@code count} bytes.
         */
        public static String bytes2HexString(byte[] b, int count) {
            String ret = "";
            for (int i = 0; i < count; ++i) {
                String hex = Integer.toHexString(b[i] & 0xFF);
                if (hex.length() == 1) hex = '0' + hex;
                ret += hex.toUpperCase();
            }
            return ret;
        }

        private static void sleepQuiet(int ms) {
            try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
        }


        /**
         * Configura o modo automático do leitor via rede e retorna o status da operação.
         * <p>
         * Monta e envia um comando ao dispositivo para habilitar/desabilitar (ou alterar)
         * o modo automático de leitura, utilizando o handle de conexão {@code hScanner}.
         * O frame de comando varia conforme o endereçamento:
         * <ul>
         *   <li><b>Address == 0</b>: comando sem endereçamento explícito
         *       ({@code [len=3, cmd=0x0F, mode, 0x00]}).</li>
         *   <li><b>Address != 0</b>: comando endereçado
         *       ({@code [len=4, cmd=0x0F, address, mode, 0x00]}).</li>
         * </ul>
         * Após o envio, a resposta é verificada: se o primeiro byte do retorno for
         * {@code 0xF4}, o segundo byte contém o código de erro do dispositivo.
         *
         * @param hScanner handle/identificador da sessão do leitor (obtido na conexão).
         * @param Mode     valor do modo automático a configurar (semântica conforme o firmware do leitor).
         * @param Address  endereço lógico do leitor; use {@code 0} quando não houver endereçamento.
         * @return {@code _OK} (0) em sucesso; código de erro do dispositivo (quando {@code ret[0] == 0xF4});
         *         {@code _net_error} em falha de transporte; ou {@code -1} em exceção.
         */
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

        /**
         * Este método faz a leitura das primeiras 8 tags e devolve o total de tags lidas
         * no comando enviado. Após as primeiras tags, verifica quantas tags faltam e chama
         * o método GetIdList para ler as tags restantes.
         * <p>
         * Parâmetros:
         * <ul>
         *   <li><b>hScanner</b> → <code>1</code> → Representa que o leitor está conectado.</li>
         *   <li><b>mem</b> → <code>1</code> → Banco de memória EPC da TAG.</li>
         *   <li><b>ptr</b> → <code>0</code> → Ponteiro inicial de leitura do banco, inicia do bit 0.</li>
         *   <li><b>len</b> → <code>0</code> → O sensor não suporta parâmetros de máscara nativamente, definido sempre como 0.</li>
         *   <li><b>mask</b> → <code>[0]</code> → Buffer de máscara para filtragem de tags específicas.
         *       O sensor não suporta nativamente parâmetros de máscara, definido sempre como 0.</li>
         *   <li><b>btID</b> → <code>[1]</code> → Buffer em que armazenamos as tags.</li>
         *   <li><b>nCounter</b> → <code>n</code> → Representa a quantidade de tags lidas na varredura.</li>
         *   <li><b>IDlen</b> → <code>[]</code> → Informa até onde os dados válidos em btID vão, pois o leitor
         *       pode não preencher o array completamente.</li>
         * </ul>
         * Ao final, o sensor retorna <code>0</code> (OK) ou <code>1</code> (Falha na leitura ou montagem do comando).
         *
         * @param hScanner
         *        1 → Scanner conectado (valor padrão)
         *
         * @param mem
         *        Define o banco de memória da tag que será acessado através do comando EPCG2.
         *        A tag armazena as seguintes informações em seus quatro bancos:
         *        <ul>
         *          <li>0 → Banco exclusivo para senhas</li>
         *          <li>1 → Banco EPC = Cabeçalho CRC + PC + EPC do produto (usar esse em 90% dos casos com TAG ID)</li>
         *          <li>2 → Banco TID = ID do fabricante e chip</li>
         *          <li>3 → Banco USER = Livre para escrita</li>
         *        </ul>
         *
         * @param ptr
         *        Valor que define a partir de qual bit começamos a leitura dentro do banco da Tag.
         *        Exemplo: Quando definimos <code>ptr = 0</code> e <code>mem = 1</code>, queremos dizer:
         *        “Comece a leitura a partir do bit 0 do banco EPC da tag”, ou seja,
         *        o leitor vai ler <code>bancoEPC[CRC16 | PC | EPC]</code>.
         *
         * @param len
         *        Define quantos bits da máscara devem ser aplicados no filtro de seleção. Trabalha sempre com bits,
         *        e não com bytes.
         *
         * @param mask
         *        Comando serve para filtragem de EPC, quando o usuário quiser que o leitor responda apenas
         *        a tags específicas detectadas. Exemplo: todas as tags com prefixo <code>3008</code> (ou 0x30, 0x08).
         *
         * @param btID
         *        Buffer onde o leitor grava as tags que ele encontrou. Cada tag vem uma atrás da outra,
         *        separadas pela contagem de bytes da tag definida em L (2 bytes).
         *
         * @param nCounter
         *        Retorna o número de tags detectadas pelo sensor.
         *
         * @param IDlen
         *        Informa até onde os dados válidos vão dentro de <code>btID</code>.
         *
         * @return
         *        <code>0</code> se OK ou <code>-1</code> se falha.
         */
        private  int Net_EPC1G2_ListTagID(int hScanner, int mem, int ptr, int len, byte[] mask, byte[] btID, int[] nCounter, int[] IDlen) {
            int res = _OK;
            byte[] put = new byte[MAX_PACKET_LEN];
            byte[] get = new byte[7680];
            if(mem == 0) mem = 1;


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

                nCounter[0] = get[1]; // -> Me devolve o total de tags Lidas, maximo de 8
                int last = 2;
                if (nCounter[0] <= 8) {
                    for(int var19 = 0; var19 < nCounter[0]; ++var19) {
                        // L em WORDS, unsigned -> Começa a Ler a partir da pos 2 de get -> get[2] = primeiro L
                        int L = get[last] & 0xFF; // -> primeiro L


                        /*
                        Se L = 0 -> sem tag , e se L > 32 ou seja a tag nao pode ter mais que 64 bytes
                        * */
                        if (L == 0 || L > 32) return _comm_error;  // sanity opcional

                        /*
                        Aqui obtemos o ID da Tag -> o id começa apos a pos 2(L) e vai ate L*2
                        por que L indica o length da tag em WORDS ou seja 2*L -> se L é 6 entao a tag tem 12 bytes
                        e vai de get[L + 1] até get[L + 1 + L*2 - 1];

                        se melhor exemplificado:
                        L = 8 -> entao o id da tag tem 16 bytes;
                        Se o ID da tag inicia apos L e L está em get[2] a tag inicia em get[3]
                        get[3] -> e o byte0 da tag, que vai até o byte15 -> Logo até get[18] (3+15);

                        next -> representa o L da próxima tag que no caso esta apos L + TAGID;
                        * */
                        int next = L * 2 + 1;

                        if (last + next > get.length) return _comm_error;

                        /*
                        Aqui copiamos o array get[], começano de last(L), para o array btID, começando de
                        L - 2 = 0, ate next que é L * 2 + 1; Ex: se L fosse 8 -> 16 + 1 -> ou seja
                        vai copiar de get[2] ate get[18]
                        * */
                        System.arraycopy(get, last, btID, last - 2, next);

                        // Last passa a ser L + next -> L + a tag inteira + 1  = proximo L
                        last += next;
                    }
                } else {
                    for(int var18 = 0; var18 < 8; ++var18) {
                        int L = get[last] & 0xFF;           // L em WORDS, unsigned
                        if (L == 0 || L > 32) return _comm_error;  // sanity opcional
                        int next = L * 2 + 1;

// e ANTES do arraycopy:
                        if (last + next > get.length) return _comm_error;
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



        /**
         * Recupera um bloco de IDs de tags (EPCs) adicionais após a leitura inicial feita por {@link #Net_EPC1G2_ListTagID}.
         * <p>
         * O leitor Fonkan FI-801 envia no máximo 8 tags por pacote. Quando há mais de 8 tags detectadas,
         * este método permite buscar as demais por meio de chamadas subsequentes, paginadas.
         * <p>
         * A função envia o comando {@code -19 (0xED)} ao leitor, especificando o índice inicial (stNum)
         * e o número de grupos (nCounter) a recuperar.
         * Cada grupo contém até 8 tags, estruturadas como:
         * {@code [L][EPC(L*2 bytes)] [L][EPC] ...}.
         *
         * <h3>Fluxo resumido:</h3>
         * <ul>
         *   <li>Envia o comando {@code 0xED} com o índice do grupo e a quantidade de páginas desejadas.</li>
         *   <li>Recebe o pacote binário contendo até {@code nCounter * 8} tags.</li>
         *   <li>Copia os EPCs para o buffer {@code btID} de forma contínua, mantendo o formato [L][EPC bytes].</li>
         *   <li>Atualiza {@code IDlen[0]} com o número total de bytes válidos em {@code btID}.</li>
         * </ul>
         *
         * <h3>Validações e erros:</h3>
         * <ul>
         *   <li>Se {@code L == 0} ou {@code L > 32}, a leitura é descartada (tag inválida).</li>
         *   <li>Se os limites de buffer forem excedidos, retorna {@code _comm_error}.</li>
         *   <li>Se {@code get[0] == -12}, o leitor retornou um erro interno.</li>
         * </ul>
         *
         * @param hScanner identificador da conexão ativa com o leitor.
         * @param btID buffer onde as tags lidas são armazenadas (cada tag = [L][EPC bytes]).
         * @param stNum índice inicial da leitura de grupo (ex.: 8, quando já foram lidas 8 tags iniciais).
         * @param nCounter número de grupos (páginas) a recuperar.
         * @param IDlen retorna, em {@code IDlen[0]}, o total de bytes válidos em {@code btID}.
         * @return {@code _OK (0)} em sucesso; {@code _net_error (134)} em falha de comunicação;
         *         {@code _comm_error (131)} em inconsistência de pacote; ou {@code -1} em exceção.
         */
        private  int Net_EPC1G2_GetIDList(int hScanner, byte[] btID, int stNum, int nCounter, int[] IDlen) {
            int res = _OK;
            byte[] put = new byte[MAX_PACKET_LEN];
            byte[] get = new byte[7680];

            try {
                put[0] = 4;
                put[1] = -19;
                put[2] = (byte)stNum; // -> indice do proximo grupo de tags no caso 8 por que ja rodamos ListTagID
                put[3] = (byte)nCounter; // quantos grupos de pagina queremos na chamada
                put[4] = 0;
                if (!wirelessTcpPacket(hScanner, put, get)) {
                    return _net_error;
                }

                if (get[0] == -12) {
                    return get[1];
                }

                int last = 2;


                for (int i = 0; i < nCounter; ++i) {
                    if (last >= get.length) return _comm_error;
                    int L = get[last] & 0xFF;
                    if (L == 0 || L > 32) return _comm_error;
                    int next = L * 2 + 1;
                    if (last + next > get.length || last - 2 + next > btID.length) return _comm_error;

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


        /**
         * Configura o intervalo de leitura do sensor no modo automático.
         * <p>
         * Este método envia dois comandos ao leitor:
         * <ol>
         *   <li><b>ReadAutoParam (0x14 / 20):</b> lê os parâmetros automáticos atuais do sensor.</li>
         *   <li><b>WriteAutoParam (0x13 / 19):</b> reescreve os parâmetros com o novo valor de intervalo.</li>
         * </ol>
         * O intervalo controla a frequência com que o leitor executa varreduras automáticas
         * e reporta as tags detectadas.
         *
         * <h3>Tabela de valores do parâmetro {@code intervalValue}</h3>
         * <pre>
         *  0 = 10ms
         *  1 = 20ms
         *  2 = 30ms (padrão)
         *  3 = 50ms
         *  4 = 100ms
         * </pre>
         *
         * <h3>Fluxo resumido:</h3>
         * <ul>
         *   <li>Valida o valor de {@code intervalValue} (0–4).</li>
         *   <li>Lê os parâmetros atuais com o comando {@code 0x14 (ReadAutoParam)}.</li>
         *   <li>Atualiza o byte de índice 3 ({@code autoParams[3]}) com o novo intervalo.</li>
         *   <li>Reenvia os parâmetros modificados com o comando {@code 0x13 (WriteAutoParam)}.</li>
         * </ul>
         *
         * <p>Em caso de falha na comunicação ou erro retornado pelo dispositivo, o método
         * imprime mensagens descritivas no console e retorna o código correspondente.</p>
         *
         * @param hScanner       identificador do scanner (geralmente 0)
         * @param intervalValue  valor do intervalo de leitura:
         *                       <ul>
         *                         <li>0 = 10ms</li>
         *                         <li>1 = 20ms</li>
         *                         <li>2 = 30ms (padrão)</li>
         *                         <li>3 = 50ms</li>
         *                         <li>4 = 100ms</li>
         *                       </ul>
         * @return código de resultado:
         *         <ul>
         *           <li>0 = sucesso</li>
         *           <li>-1 = erro de parâmetro</li>
         *           <li>{@code _net_error} = falha de comunicação TCP</li>
         *           <li>ou código de erro específico retornado pelo dispositivo (0xF4)</li>
         *         </ul>
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
         * Lê o bloco de 32 bytes de parâmetros básicos de operação do leitor Fonkan FI-801.
         * <p>
         * Este método envia o comando {@code 0x06 (Read Param)} ao leitor, recebendo como resposta
         * um bloco de 32 bytes contendo as configurações atuais do dispositivo, como baud rate,
         * potência de transmissão, modo de operação, tipo de tag configurada, entre outros.
         * <p>
         * Os dados recebidos são processados internamente pelo método {@link #ParseParams(byte[])}
         * para exibir informações detalhadas sobre o estado e a configuração atual do sensor.
         *
         * @param hScanner identificador do scanner (geralmente 1).
         * @return um array de 32 bytes com os parâmetros em caso de sucesso, ou {@code null} em caso de falha.
         */
        public byte[] readParams(int hScanner) {
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

                ParseParams(params);

                return params;

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        /**
         * Interpreta e exibe os 32 bytes de parâmetros retornados pelo comando {@code Read Param (0x06)}.
         * <p>
         * Este método converte cada campo do array {@code parametros} em informações legíveis,
         * como baud rate da porta serial, potência de transmissão, modo de leitura, tipo da tag,
         * tempo de varredura, endereço IP, máscara, gateway e MAC Address.
         * <p>
         * Os campos e seus significados são definidos conforme o manual técnico do leitor Fonkan FI-801.
         *
         * @param parametros array de 32 bytes retornado pelo comando {@link #readParams(int)}.
         */
        private void ParseParams(byte[] parametros){
            Map<Integer, Integer> BaudRates = Map.of(4,9600,
                    5,19200,
                    6,38400,
                    7,57600,
                    8,115200);
            Map<Integer, String> TagTypes = Map.of(1,"ISO18000-6B",
                    2,"EPCC1",
                    4,"EPCC1G2",
                    8,"EM4442",
                    10,"ATA");
            Map<Integer, String> ReadingDurationTimes = Map.of(0, "10ms",
                    1,"20ms",
                    2,"30ms",
                    3,"40ms");

            int hScanner = 1;
            byte[] ip = new byte [4];
            byte[] port = new byte[2];
            byte[] mask = new byte[4];
            byte[] gateway = new byte[4];
            byte[] macAdress = new byte[6];
            int BaudRate = parametros[0] & 0xFF;
            int TransmissionPower = parametros[1] & 0xFF;
            int ReaderMode = parametros[5] & 0xFF;
            int TagType = parametros[8] & 0xFF;
            int ReadinDuration = parametros[9] & 0xFF;

            System.out.println("Sensor conectado com sucesso");
            System.out.println("================DADOS DO SENSOR===================");

            System.out.println("Baud Rate da porta Serial: "+ BaudRates.get(BaudRate)+"bps");

            System.out.println("Potencia de transmissão: "+ TransmissionPower);

            StringJoiner sj = new StringJoiner(" ~ ");
            sj.add(String.valueOf(parametros[2] & 0xFF));
            sj.add(String.valueOf(parametros[3] & 0xFF));
            System.out.println("Ponto inicial - final da frequência: "+sj );

            System.out.println("Profundidade de modulação: "+ (parametros[4] & 0xFF));

            System.out.println("Modo ativo do sensor: " + (ReaderMode == 0 ? "Auto - 0 " : "Command - 1"));

            System.out.println("Máximo de Tags por leitura: " + (parametros[8] & 0xFF));

            System.out.println("Tipo da Tag Configurada : " + TagTypes.get(TagType));

            System.out.println("Duração do tempo de leitura da tag: " + ReadingDurationTimes.get(ReadinDuration));

            System.out.println("Quantidade de Leituras por requisição: " + (parametros[10] & 0xFF));

            System.out.println("Alarme (buzzer) de leitura ligado :" + ((parametros[11] & 0xFF) == 0 ? "Desligado - 0" : "Ligado - 1"));

            System.arraycopy(parametros,12, ip, 0, 4);
            sj = new StringJoiner(".");
            for (byte b : ip){
                sj.add(String.valueOf((b & 0xFF)));
            }
            System.out.println("Endereço ip: "+ sj);

            System.arraycopy(parametros, 16, port, 0, 2);
            StringBuilder sb = new StringBuilder();
            for (byte b : port) {
                sb.append(b & 0xFF);
            }
            System.out.println("Porta: " + sb);

            System.arraycopy(parametros, 18, mask, 0, 4);
            sj = new StringJoiner(".");
            for (byte b : mask) {
                sj.add(String.valueOf(b & 0xFF));
            }
            System.out.println("Mascara: "+ sj);

            sj = new StringJoiner(".");
            System.arraycopy(parametros, 22,gateway, 0, 4);
            for(byte b : gateway){
                sj.add(String.valueOf(b & 0xFF));
            }
            System.out.println("Gateway: " + sj);

            sj = new StringJoiner(":");
            System.arraycopy(parametros, 26, macAdress, 0, 6);
            for (byte b : macAdress){
                sj.add(String.format("%02X",b & 0xFF));
            }
            System.out.println("Mac: "+sj);
        }

    }
