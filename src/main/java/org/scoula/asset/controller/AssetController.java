package org.scoula.asset.controller;

import org.scoula.asset.dto.AssetDashboardResDTO;
import org.scoula.asset.service.AssetService;
import org.scoula.security.account.domain.CustomUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/asset")
public class AssetController {
    @Autowired
    private AssetService assetService;

    @GetMapping("/dashboard")
    public ResponseEntity<AssetDashboardResDTO> getDashboard(@AuthenticationPrincipal CustomUser user) {
        Integer memberNo = user.getMember().getMemberNo();
        if (memberNo == null) {
            return ResponseEntity.status(401).build();
        }

        AssetDashboardResDTO response = assetService.getAssetDashboard(memberNo);
        return ResponseEntity.ok(response);
    }
}
