package org.Yan.service;

import org.Yan.infra.DTO.TagDto;

import java.util.List;

public interface ISensorService {
    List<TagDto> GetAll(String ip, int port);
    TagDto GetById(String tagId, String ip, int port);

}
