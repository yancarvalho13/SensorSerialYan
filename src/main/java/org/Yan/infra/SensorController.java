package org.Yan.infra;

import org.Yan.driver.WirelessReader;
import org.Yan.infra.DTO.SensorDataDTO;
import org.Yan.infra.DTO.SensorResponseDTO;
import org.Yan.infra.DTO.TagDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@RestController
@RequestMapping("/sensor")
@CrossOrigin("*")
public class SensorController {
    private static final Logger log = LoggerFactory.getLogger(SensorController.class);

    @GetMapping
    @CrossOrigin("*")
    public ResponseEntity<?> getAllTags( ){
        log.info("Buscando tags...");

        Set<String> tags = new HashSet<>();
        var response = new ArrayList<TagDto>();

        try(Socket socket = new Socket( "192.168.2.1", 6000)){
            WirelessReader r = new WirelessReader(socket);
            short[] hv = new short[1], sv = new short[1];
            int hScanner = 0;
            tags = r.Net_getDistinctTags(0);
            tags.stream().forEach(tag -> {
                response.add(new TagDto(tag, "Retifica Mecânica"));
            });
            if(tags.isEmpty()){
                return ResponseEntity.ok("Nenhuma tag encontrada");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(response);
    }
}
