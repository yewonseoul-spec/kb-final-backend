package org.scoula.consumption.dto;

// 일별 예상 소비 1건
public class ExpectedItemDTO {
    private Long expectedNo;
    private String categoryName;
    private Long amount;
    private String merchant;

    public Long getExpectedNo() {
        return expectedNo;
    }

    public void setExpectedNo(Long expectedNo) {
        this.expectedNo = expectedNo;
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
