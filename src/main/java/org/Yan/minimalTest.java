package org.Yan;

import org.Yan.driver.WirelessReader;

import java.net.Socket;

public class minimalTest {

    private static void ProxyRunner(){
        try(Socket socket = new Socket("192.168.2.1", 6000 )){
            WirelessReader r = new WirelessReader(socket);
            short[] hv = new short[1], sv = new short[1];
            int hScanner = 0;
            var tags = r.Net_getDistinctTags(0);
            System.out.println(tags);

        }catch (Exception e){
            e.printStackTrace();
        }

    }





}
