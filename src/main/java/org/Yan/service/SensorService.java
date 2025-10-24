package org.Yan.service;

import org.Yan.driver.WirelessReader;
import org.Yan.exceptions.TagListNotFoundException;
import org.Yan.exceptions.TagNotFoundException;
import org.Yan.infra.DTO.TagDto;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SensorService implements ISensorService{
    private  WirelessReader sensor;

    @Override
    public List<TagDto> GetAll(String ip, int port) {
        Set<String> tags = new HashSet<>();
        var response = new ArrayList<TagDto>();

        try(Socket socket = new Socket( ip, port)){
            sensor = new WirelessReader(socket);
            short[] hv = new short[1], sv = new short[1];
            int hScanner = 0;
            tags = sensor.Net_getDistinctTags(0);
            if(tags.isEmpty()){
                throw new TagListNotFoundException(ip);
            }
            tags.forEach(tag -> {
                response.add(new TagDto(tag, "Retifica Mecânica"));
            });

        }catch (Exception e){
            throw new RuntimeException(e);
        }
        return response;
    }

    @Override
    public TagDto GetById(String tagId, String ip, int port) {
        var findedTag = new TagDto("", "");
        try(Socket socket = new Socket(ip, port)){
            sensor = new WirelessReader(socket);
            var tags = sensor.Net_getDistinctTags(1);
            if(tags.isEmpty()){
                throw new TagListNotFoundException(ip);
            }
            var tag = tags.stream().filter(t -> t.equalsIgnoreCase(tagId)).findFirst();
            if(tag.isEmpty()){
                 throw new TagNotFoundException(tagId);
            }
            findedTag = new TagDto(tag.get(),"Setor 01");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return findedTag;
    }
}
