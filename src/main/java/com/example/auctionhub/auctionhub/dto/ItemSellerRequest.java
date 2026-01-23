package com.example.auctionhub.auctionhub.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.example.auctionhub.auctionhub.models.Item;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ItemSellerRequest {
	private String title;
	private String description;
	private List<ItemImages> imagesUrlList;
	private Item.ItemCategory category;
	private BigDecimal startingPrice;
	private LocalDateTime startDate;
	private LocalDateTime endDate;

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ItemImages {
		
		private String url;
		private int index;
	}
}
