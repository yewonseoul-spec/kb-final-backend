package org.scoula.board.controller;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.scoula.board.domain.BoardAttachmentVO;
import org.scoula.board.dto.BoardDTO;
import org.scoula.board.service.BoardService;
import org.scoula.common.util.UploadFiles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
@Log4j2
@Api(tags = "게시글 관리")
public class BoardController {
    private final BoardService service;

    @GetMapping("")
    public ResponseEntity<List<BoardDTO>> getList() {
        return ResponseEntity.ok(service.getList());
    }

    @GetMapping("/{no}")
    public ResponseEntity<BoardDTO> get(
            @ApiParam(value = "게시글 ID", required = true, example = "1")
            @PathVariable Long no) {
        return ResponseEntity.ok(service.get(no));
    }

    @PostMapping("")
    public ResponseEntity<BoardDTO> create(
            @ApiParam(value = "게시글 객체", required = true)
            BoardDTO board) {
        return ResponseEntity.ok(service.create(board));
    }


    @PutMapping("/{no}")
    public ResponseEntity<BoardDTO> update(
            @ApiParam(value = "게시글 ID", required = true, example = "1")
            @PathVariable Long no,
            @ApiParam(value = "게시글 객체", required = true)
            @RequestBody BoardDTO board) {
        return ResponseEntity.ok(service.update(board));
    }


    @DeleteMapping("/{no}")
    public ResponseEntity<BoardDTO> delete(
            @PathVariable
            @ApiParam(value = "게시글 ID", required = true, example = "1")
            Long no) {
        return ResponseEntity.ok(service.delete(no));
    }

    @GetMapping("/download/{no}")
    public void download(@PathVariable Long no, HttpServletResponse response) throws Exception {
        BoardAttachmentVO attachment = service.getAttachment(no);
        File file = new File(attachment.getPath());
        UploadFiles.download(response, file, attachment.getFilename());
    }


    @DeleteMapping("/deleteAttachment/{no}")
    public ResponseEntity<Boolean> deleteAttachment(@PathVariable Long no) throws Exception {
        return ResponseEntity.ok(service.deleteAttachment(no));

    }
    public String test;

}
