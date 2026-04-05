package com.example.auctionhub.auctionhub.service;

import com.example.auctionhub.auctionhub.dto.ItemDetailDTO;
import com.example.auctionhub.auctionhub.dto.ItemSearchRequest;
import com.example.auctionhub.auctionhub.dto.ItemSearchResponse;
import com.example.auctionhub.auctionhub.dto.ItemSearchResultDTO;
import com.example.auctionhub.auctionhub.dto.TopFiveItemResponse;
import com.example.auctionhub.auctionhub.mapper.ItemDetailMapper;
import com.example.auctionhub.auctionhub.mapper.ItemSearchResultDTOMapper;
import com.example.auctionhub.auctionhub.mapper.TopFiveItemMapper;
import com.example.auctionhub.auctionhub.models.Bid;
import com.example.auctionhub.auctionhub.models.Item;
import com.example.auctionhub.auctionhub.models.ItemImages;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.redis.TrackTopViewedItems;
import com.example.auctionhub.auctionhub.repository.ItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemBidderServiceTest {

    @Mock private ItemRepository itemRepository;
    @Mock private TrackTopViewedItems trackTopViewedItems;
    @Mock private ItemImageService itemImageService;
    @Mock private TopFiveItemMapper topFiveItemMapper;
    @Mock private ItemSearchResultDTOMapper itemSearchResultDTOMapper;
    @Mock private ItemDetailMapper itemDetailMapper;

    @InjectMocks
    private ItemBidderService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private User buildUser(Long id, User.roles role) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        u.setRole(role);
        u.setUserStatus(User.status.active);
        return u;
    }

    private Item buildItem(Long id, User seller) {
        Item item = new Item();
        item.setId(id);
        item.setTitle("Test Item " + id);
        item.setDescription("desc");
        item.setCategory(Item.ItemCategory.ELECTRONICS);
        item.setStatus(Item.ItemStatus.ACTIVE);
        item.setStartingPrice(new BigDecimal("50.00"));
        item.setCurrentHighestBid(new BigDecimal("100.00"));
        item.setStartDate(LocalDateTime.now().minusDays(1));
        item.setEndDate(LocalDateTime.now().plusDays(1));
        item.setSeller(seller);
        return item;
    }

    private Bid buildBid(User bidder, Item item, BigDecimal amount) {
        Bid bid = new Bid();
        bid.setUser(bidder);
        bid.setBidAmount(amount);
        bid.setCreatedAt(LocalDateTime.now());
        return bid;
    }

    private void authenticateAs(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ══════════════════════════════════════════════════════════════════════
    // getTopFiveItems
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void getTopFiveItems_emptyRedisSet_returnsEmptyList() {
        when(trackTopViewedItems.getTopFiveViewedItems()).thenReturn(Set.of());

        assertThat(service.getTopFiveItems()).isEmpty();
        verifyNoInteractions(itemRepository, itemImageService, topFiveItemMapper);
    }

    @Test
    void getTopFiveItems_nullRedisSet_returnsEmptyList() {
        when(trackTopViewedItems.getTopFiveViewedItems()).thenReturn(null);

        assertThat(service.getTopFiveItems()).isEmpty();
    }

    @Test
    void getTopFiveItems_invalidIdInRedis_returnsEmptyList() {
        when(trackTopViewedItems.getTopFiveViewedItems()).thenReturn(Set.of("not-a-number"));

        assertThat(service.getTopFiveItems()).isEmpty();
    }

    @Test
    void getTopFiveItems_success_returnsRankedItems() {
        User seller = buildUser(2L, User.roles.POSTER);
        Item item = buildItem(10L, seller);

        when(trackTopViewedItems.getTopFiveViewedItems()).thenReturn(Set.of("10"));
        when(itemRepository.findAllById(anyList())).thenReturn(List.of(item));
        when(itemImageService.getFirstImageIndexForAllItems(anyList())).thenReturn(Map.of(10L, "http://img.jpg"));

        TopFiveItemResponse response = new TopFiveItemResponse();
        when(topFiveItemMapper.toTopFiveItemResponse(item)).thenReturn(response);

        List<TopFiveItemResponse> result = service.getTopFiveItems();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRank()).isEqualTo(1);
        assertThat(result.get(0).getFirstIndexUrlImage()).isEqualTo("http://img.jpg");
    }

    @Test
    void getTopFiveItems_itemDeletedFromDb_skipped() {
        when(trackTopViewedItems.getTopFiveViewedItems()).thenReturn(Set.of("99"));
        when(itemRepository.findAllById(anyList())).thenReturn(List.of()); // item 99 gone
        when(itemImageService.getFirstImageIndexForAllItems(anyList())).thenReturn(Map.of());

        List<TopFiveItemResponse> result = service.getTopFiveItems();

        assertThat(result).isEmpty();
    }

    @Test
    void getTopFiveItems_exceptionInRedis_returnsEmptyList() {
        when(trackTopViewedItems.getTopFiveViewedItems()).thenThrow(new RuntimeException("Redis down"));

        assertThat(service.getTopFiveItems()).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════════════
    // searchItems — validation
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void searchItems_keywordTooLong_throws() {
        ItemSearchRequest req = ItemSearchRequest.builder()
            .keyword("x".repeat(101))
            .build();

        assertThatThrownBy(() -> service.searchItems(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("keyword too long");
    }

    @Test
    void searchItems_negativeMinPrice_throws() {
        ItemSearchRequest req = ItemSearchRequest.builder()
            .minPrice(new BigDecimal("-1"))
            .build();

        assertThatThrownBy(() -> service.searchItems(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Minimum price cannot be negative");
    }

    @Test
    void searchItems_negativeMaxPrice_throws() {
        ItemSearchRequest req = ItemSearchRequest.builder()
            .maxPrice(new BigDecimal("-0.01"))
            .build();

        assertThatThrownBy(() -> service.searchItems(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Maximum price cannot be negative");
    }

    @Test
    void searchItems_minPriceGreaterThanMaxPrice_throws() {
        ItemSearchRequest req = ItemSearchRequest.builder()
            .minPrice(new BigDecimal("200"))
            .maxPrice(new BigDecimal("100"))
            .build();

        assertThatThrownBy(() -> service.searchItems(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Minimum price cannot be greater than maximum price");
    }

    @Test
    void searchItems_endingInHoursTooLow_throws() {
        ItemSearchRequest req = ItemSearchRequest.builder()
            .endingInHours(0)
            .build();

        assertThatThrownBy(() -> service.searchItems(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endingInHours must be between 1 and 720");
    }

    @Test
    void searchItems_endingInHoursTooHigh_throws() {
        ItemSearchRequest req = ItemSearchRequest.builder()
            .endingInHours(721)
            .build();

        assertThatThrownBy(() -> service.searchItems(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endingInHours must be between 1 and 720");
    }

    // ══════════════════════════════════════════════════════════════════════
    // searchItems — success paths
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @SuppressWarnings("unchecked")
    void searchItems_success_returnsMappedPage() {
        User seller = buildUser(2L, User.roles.POSTER);
        Item item = buildItem(10L, seller);

        when(itemRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(item)));
        when(itemImageService.getFirstImageIndexForAllItems(anyList()))
            .thenReturn(Map.of(10L, "http://img.jpg"));

        ItemSearchResultDTO dto = new ItemSearchResultDTO();
        when(itemSearchResultDTOMapper.toItemSearchResponse(item)).thenReturn(dto);

        ItemSearchRequest req = ItemSearchRequest.builder().keyword("laptop").build();
        ItemSearchResponse result = service.searchItems(req);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchItems_defaultPaginationAndSort_usedWhenNull() {
        when(itemRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(itemImageService.getFirstImageIndexForAllItems(anyList()))
            .thenReturn(Map.of());

        // All nulls → should not throw, uses page=0, size=20, sort=endDate ASC
        ItemSearchRequest req = ItemSearchRequest.builder().build();
        ItemSearchResponse result = service.searchItems(req);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getCurrentPage()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchItems_setsTimeRemainingAndImageUrlOnEachDTO() {
        User seller = buildUser(2L, User.roles.POSTER);
        Item item = buildItem(10L, seller);
        item.setEndDate(LocalDateTime.now().plusHours(2));

        when(itemRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(item)));
        when(itemImageService.getFirstImageIndexForAllItems(anyList()))
            .thenReturn(Map.of(10L, "http://thumb.jpg"));

        ItemSearchResultDTO dto = new ItemSearchResultDTO();
        when(itemSearchResultDTOMapper.toItemSearchResponse(item)).thenReturn(dto);

        service.searchItems(ItemSearchRequest.builder().build());

        // Both fields are enriched after mapper call
        assertThat(dto.getTimeRemaining()).isNotNull();
        assertThat(dto.getImagesUrlList()).isEqualTo("http://thumb.jpg");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchItems_missingImageInMap_fallsBackToEmptyString() {
        User seller = buildUser(2L, User.roles.POSTER);
        Item item = buildItem(10L, seller);

        when(itemRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(item)));
        when(itemImageService.getFirstImageIndexForAllItems(anyList()))
            .thenReturn(Map.of()); // item 10 has no image entry

        ItemSearchResultDTO dto = new ItemSearchResultDTO();
        when(itemSearchResultDTOMapper.toItemSearchResponse(item)).thenReturn(dto);

        service.searchItems(ItemSearchRequest.builder().build());

        assertThat(dto.getImagesUrlList()).isEqualTo("");
    }

    // ══════════════════════════════════════════════════════════════════════
    // getItemDetail
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void getItemDetail_itemNotFound_throws() {
        User user = buildUser(1L, User.roles.USER);
        authenticateAs(user);
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getItemDetail(99L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Item not found");
    }

    @Test
    void getItemDetail_nullRole_throws() {
        // role.name() blows up before any repository call is made
        User user = buildUser(1L, User.roles.ADMIN);
        user.setRole(null);
        authenticateAs(user);

        assertThatThrownBy(() -> service.getItemDetail(10L))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getItemDetail_userRole_hasUserBid_true() {
        User currentUser = buildUser(1L, User.roles.USER);
        authenticateAs(currentUser);

        User seller = buildUser(2L, User.roles.POSTER);
        Item item = buildItem(10L, seller);
        item.setBids(List.of(buildBid(currentUser, item, new BigDecimal("150"))));

        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemImageService.findAllByItemIdOrderByIndexAsc(10L)).thenReturn(List.of());
        when(itemDetailMapper.toDto(item)).thenReturn(new ItemDetailDTO());

        ItemDetailDTO result = service.getItemDetail(10L);

        assertThat(result.getHasUserBid()).isTrue();
        assertThat(result.getIsUserSeller()).isFalse();
        assertThat(result.getRecentBids()).hasSize(1);
    }

    @Test
    void getItemDetail_userRole_hasUserBid_false() {
        User currentUser = buildUser(1L, User.roles.USER);
        authenticateAs(currentUser);

        User seller = buildUser(2L, User.roles.POSTER);
        User otherBidder = buildUser(3L, User.roles.USER);
        Item item = buildItem(10L, seller);
        item.setBids(List.of(buildBid(otherBidder, item, new BigDecimal("200"))));

        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemImageService.findAllByItemIdOrderByIndexAsc(10L)).thenReturn(List.of());
        when(itemDetailMapper.toDto(item)).thenReturn(new ItemDetailDTO());

        ItemDetailDTO result = service.getItemDetail(10L);

        assertThat(result.getHasUserBid()).isFalse();
        assertThat(result.getIsUserSeller()).isFalse();
    }

    @Test
    void getItemDetail_userRole_nullBids_handledGracefully() {
        User currentUser = buildUser(1L, User.roles.USER);
        authenticateAs(currentUser);

        User seller = buildUser(2L, User.roles.POSTER);
        Item item = buildItem(10L, seller);
        item.setBids(null); // no bids at all

        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemImageService.findAllByItemIdOrderByIndexAsc(10L)).thenReturn(List.of());
        when(itemDetailMapper.toDto(item)).thenReturn(new ItemDetailDTO());

        ItemDetailDTO result = service.getItemDetail(10L);

        assertThat(result.getHasUserBid()).isFalse();
        assertThat(result.getBidCount()).isZero();
        assertThat(result.getRecentBids()).isEmpty();
    }

    @Test
    void getItemDetail_posterRole_isOwner_getsRecentBids() {
        User seller = buildUser(2L, User.roles.POSTER);
        authenticateAs(seller); // poster IS the seller

        Item item = buildItem(10L, seller);
        User bidder = buildUser(3L, User.roles.USER);
        item.setBids(List.of(buildBid(bidder, item, new BigDecimal("300"))));

        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemImageService.findAllByItemIdOrderByIndexAsc(10L)).thenReturn(List.of());
        when(itemDetailMapper.toDto(item)).thenReturn(new ItemDetailDTO());

        ItemDetailDTO result = service.getItemDetail(10L);

        assertThat(result.getIsUserSeller()).isTrue();
        assertThat(result.getRecentBids()).hasSize(1); // owner sees bids
        assertThat(result.getHasUserBid()).isFalse();
    }

    @Test
    void getItemDetail_posterRole_notOwner_emptyRecentBids() {
        User currentPoster = buildUser(5L, User.roles.POSTER); // different poster
        authenticateAs(currentPoster);

        User seller = buildUser(2L, User.roles.POSTER);
        Item item = buildItem(10L, seller);
        User bidder = buildUser(3L, User.roles.USER);
        item.setBids(List.of(buildBid(bidder, item, new BigDecimal("300"))));

        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemImageService.findAllByItemIdOrderByIndexAsc(10L)).thenReturn(List.of());
        when(itemDetailMapper.toDto(item)).thenReturn(new ItemDetailDTO());

        ItemDetailDTO result = service.getItemDetail(10L);

        assertThat(result.getIsUserSeller()).isFalse();
        assertThat(result.getRecentBids()).isEmpty(); // not owner — no bid details
    }

    @Test
    void getItemDetail_adminRole_fullAccess() {
        User admin = buildUser(9L, User.roles.ADMIN);
        authenticateAs(admin);

        User seller = buildUser(2L, User.roles.POSTER);
        Item item = buildItem(10L, seller);
        User bidder = buildUser(3L, User.roles.USER);
        item.setBids(List.of(buildBid(bidder, item, new BigDecimal("500"))));

        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemImageService.findAllByItemIdOrderByIndexAsc(10L)).thenReturn(List.of());
        when(itemDetailMapper.toDto(item)).thenReturn(new ItemDetailDTO());

        ItemDetailDTO result = service.getItemDetail(10L);

        assertThat(result.getHasUserBid()).isFalse();
        assertThat(result.getIsUserSeller()).isFalse();
        assertThat(result.getRecentBids()).hasSize(1); // admin sees all bids
    }

    @Test
    void getItemDetail_imageUrlsAreSetOnResult() {
        User user = buildUser(1L, User.roles.USER);
        authenticateAs(user);

        User seller = buildUser(2L, User.roles.POSTER);
        Item item = buildItem(10L, seller);

        ItemImages img = new ItemImages();
        img.setUrl("http://image1.jpg");

        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemImageService.findAllByItemIdOrderByIndexAsc(10L)).thenReturn(List.of(img));
        when(itemDetailMapper.toDto(item)).thenReturn(new ItemDetailDTO());

        ItemDetailDTO result = service.getItemDetail(10L);

        assertThat(result.getImageUrls()).containsExactly("http://image1.jpg");
    }

    @Test
    void getItemDetail_recentBidsLimitedToFive() {
        User user = buildUser(1L, User.roles.USER);
        authenticateAs(user);

        User seller = buildUser(2L, User.roles.POSTER);
        Item item = buildItem(10L, seller);

        // Create 7 bids — only 5 most recent should be returned
        User bidder = buildUser(3L, User.roles.USER);
        List<Bid> bids = List.of(
            buildBid(bidder, item, new BigDecimal("100")),
            buildBid(bidder, item, new BigDecimal("110")),
            buildBid(bidder, item, new BigDecimal("120")),
            buildBid(bidder, item, new BigDecimal("130")),
            buildBid(bidder, item, new BigDecimal("140")),
            buildBid(bidder, item, new BigDecimal("150")),
            buildBid(bidder, item, new BigDecimal("160"))
        );
        item.setBids(bids);

        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemImageService.findAllByItemIdOrderByIndexAsc(10L)).thenReturn(List.of());
        when(itemDetailMapper.toDto(item)).thenReturn(new ItemDetailDTO());

        ItemDetailDTO result = service.getItemDetail(10L);

        assertThat(result.getRecentBids()).hasSize(5);
    }
}
