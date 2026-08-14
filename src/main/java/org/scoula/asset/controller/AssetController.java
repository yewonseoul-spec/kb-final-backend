package org.scoula.asset.controller;

import org.scoula.asset.dto.AccountDTO;
import org.scoula.asset.dto.AssetDashboardResDTO;
import org.scoula.asset.dto.AssetRatioResDTO;
import org.scoula.asset.service.AssetService;
import org.scoula.security.account.domain.CustomUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/asset")
public class AssetController {
    @Autowired
    private AssetService assetService;

    // 자산 대시보드
    @GetMapping("/dashboard")
    public ResponseEntity<AssetDashboardResDTO> getDashboard(@AuthenticationPrincipal CustomUser user) {
        Integer memberNo = user.getMember().getMemberNo();
        if (memberNo == null) {
            return ResponseEntity.status(401).build();
        }

        AssetDashboardResDTO response = assetService.getAssetDashboard(memberNo);
        return ResponseEntity.ok(response);
    }

    // 자산 구성 비율 조회
    @GetMapping("/ratio")
    public ResponseEntity<AssetRatioResDTO> getAssetRatio(HttpServletRequest request, @AuthenticationPrincipal CustomUser user) {
        Integer memberNo = user.getMember().getMemberNo();
        if (memberNo == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(assetService.getAssetRatio(memberNo));
    }

    // 계좌별 잔액 조회
    @GetMapping("/balance")
    public ResponseEntity<List<AccountDTO>> getAccountBalances(HttpServletRequest request, @AuthenticationPrincipal CustomUser user) {
        Integer memberNo = user.getMember().getMemberNo();
        if (memberNo == null) {
            return ResponseEntity.status(401).build();
        }

        List<AccountDTO> accounts = assetService.getAccountBalances(memberNo);
        return ResponseEntity.ok(accounts);
    }
}
