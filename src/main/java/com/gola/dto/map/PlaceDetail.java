package com.gola.dto.map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceDetail {
    private String name;           // tên tiếng Việt
    private String nameEn;         // tên tiếng Anh
    private Double lat;
    private Double lng;
    private String address;        // địa chỉ đầy đủ
    private String openingHours;   // giờ mở cửa
    private String phone;
    private String website;
    private Boolean hasFee;        // có tính phí không
    private Boolean wheelchair;    // hỗ trợ xe lăn
    private String imageUrl;       // URL ảnh từ Wikimedia
    private String wikidataId;     // để dùng sau nếu cần
    private List<String> photoUrls;
    private BigDecimal rating;
    private Integer reviewCount;
    private String imageSource;
    private String placeAddress;
    private String dataSource;
    private String enrichmentStatus;
    private Boolean hasRealPhoto;
    private Boolean hasRealCoordinates;
    private Boolean hasOpeningHours;
    private Boolean openNow;
    private String businessStatus;
    private String nextOpenCloseText;
    private BigDecimal estimatedCost;
    private String providerTitle;
    private String providerSource;
    private String providerId;
    private String rejectedReason;
}
