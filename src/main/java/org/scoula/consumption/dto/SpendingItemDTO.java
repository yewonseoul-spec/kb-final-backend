package org.scoula.consumption.dto;

// 일별 소비 내역 1건
public class SpendingItemDTO {
    private Long spendingNo;
    private String categoryName;
    private Long amount;
    private String merchant;

    public Long getSpendingNo() {
        return spendingNo;
    }

    public void setSpendingNo(Long spendingNo) {
        this.spendingNo = spendingNo;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }
}
