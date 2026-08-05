package org.scoula.asset.controller;

import org.scoula.asset.dto.AssetDashboardResDTO;
import org.scoula.asset.service.AssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/asset")
public class AssetController {
    @Autowired
    private AssetService assetService;

    @GetMapping("/dashboard")
    public ResponseEntity<AssetDashboardResDTO> getDashboard(HttpSession session) {
        Integer memberNo = (Integer) session.getAttribute("memberNo");
        if (memberNo == null) {
            return ResponseEntity.status(401).build();
        }

        AssetDashboardResDTO response = assetService.getAssetDashboard(memberNo);
        return ResponseEntity.ok(response);
    }
}
