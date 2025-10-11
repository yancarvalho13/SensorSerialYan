package org.Yan.infra;

import org.Yan.driver.WirelessReader;
import org.Yan.infra.DTO.SensorDataDTO;
import org.Yan.infra.DTO.SensorResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/sensor")
@CrossOrigin("*")
public class SensorController {


    @GetMapping
    @CrossOrigin("*")
    public ResponseEntity<?> getAllTags( ){
        Set<String> tags = new HashSet<>();
        SensorResponseDTO response = new SensorResponseDTO(Set.of(), 0);
        try(Socket socket = new Socket( "192.168.2.1", 6000)){
            WirelessReader r = new WirelessReader(socket);
            short[] hv = new short[1], sv = new short[1];
            int hScanner = 0;
            tags = r.Net_getDistinctTags(0);
            response = new SensorResponseDTO(tags, tags.size());
            if(tags.isEmpty()){
                return ResponseEntity.ok("Nenhuma tag encontrada");
            }

        }catch (Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(response);
    }
}
