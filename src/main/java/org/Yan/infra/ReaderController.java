package org.Yan.infra;

import org.Yan.driver.Reader;
import org.Yan.driver.WirelessReader;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Socket;
import java.util.Set;

@RestController
@RequestMapping("/api/leitor")
public class ReaderController {

    @GetMapping("/hello")
    public ResponseEntity<String> hello(){
        return ResponseEntity.ok("Hello World");
    }

    @GetMapping
    public ResponseEntity<?> getAllTagsInRange(){
        try(Socket socket = new Socket("192.168.2.1", 6000 )){

            WirelessReader reader = new WirelessReader(socket);
            short[] hv = new short[1], sv = new short[1];
            reader.wirelessGetReaderVersion(0, hv, sv);   // internamente chame Net_PacketTcp
            System.out.println("HW=" + hv[0] + "  SW=" + sv[0]);
            Set<String> tags = reader.Net_getDistinctTags();  // usa os métodos *Net_* que chamam Net_PacketTcp
            System.out.println(tags);;
            return ResponseEntity.ok().body(tags);



        }catch (Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.badRequest().body("Falhou");
    }


}
