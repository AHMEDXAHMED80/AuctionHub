package com.example.auctionhub.auctionhub.repository;

import com.example.auctionhub.auctionhub.models.ItemImages;

import io.lettuce.core.dynamic.annotation.Param;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemImagesRepository extends JpaRepository<ItemImages, Long> {
        @Query("SELECT i FROM ItemImages i WHERE i.item.id = ?1 ORDER BY i.index ASC")
        java.util.List<ItemImages> findAllByItemIdOrderByIndexAsc(Long itemId);
    
        @Query(
        "SELECT i FROM ItemImages i " +
        "WHERE i.item.id = ?1 AND i.index = ?2"
        )
        Optional<ItemImages> findImageByIndex(Long itemId, int index); // <-- Use camelCase


        @Query ("SELECT i.item.id, i.url FROM ItemImages i WHERE i.item.id IN :itemId AND i.index = 0")
        List<Object[]> findFirstImageIndexByitemId(@Param("itemId") List<Long> itemId);
}
