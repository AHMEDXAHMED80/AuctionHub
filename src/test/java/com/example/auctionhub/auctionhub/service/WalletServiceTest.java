package com.example.auctionhub.auctionhub.service;

import com.example.auctionhub.auctionhub.mapper.WalletMapper;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.models.Wallet;
import com.example.auctionhub.auctionhub.models.Wallet.walletType;
import com.example.auctionhub.auctionhub.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletMapper walletMapper;

    @InjectMocks private WalletService walletService;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(User.roles.USER);

        wallet = new Wallet();
        wallet.setId(1L);
        wallet.setUser(user);
        wallet.setAvailableBalance(new BigDecimal("500.00"));
        wallet.setFrozenBalance(new BigDecimal("50.00"));
        wallet.setWalletType(walletType.BIDDER);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User u) {
        var auth = new UsernamePasswordAuthenticationToken(u, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -----------------------------------------------------------------------
    // createWallet
    // -----------------------------------------------------------------------

    @Test
    void createWallet_userRole_createsBidderWallet() {
        user.setRole(User.roles.USER);
        authenticateAs(user);
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        walletService.createWallet();

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        assertEquals(walletType.BIDDER, captor.getValue().getWalletType());
    }

    @Test
    void createWallet_posterRole_createsSellerWallet() {
        user.setRole(User.roles.POSTER);
        authenticateAs(user);
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        walletService.createWallet();

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        assertEquals(walletType.SELLER, captor.getValue().getWalletType());
    }

    @Test
    void createWallet_adminRole_createsSellerWallet() {
        user.setRole(User.roles.ADMIN);
        authenticateAs(user);
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        walletService.createWallet();

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        assertEquals(walletType.SELLER, captor.getValue().getWalletType());
    }

    // -----------------------------------------------------------------------
    // addBalance
    // -----------------------------------------------------------------------

    @Test
    void addBalance_nullAmount_throwsIllegalArgumentException() {
        authenticateAs(user);
        assertThrows(IllegalArgumentException.class, () -> walletService.addBalance(null));
    }

    @Test
    void addBalance_zeroAmount_throwsIllegalArgumentException() {
        authenticateAs(user);
        assertThrows(IllegalArgumentException.class, () -> walletService.addBalance(BigDecimal.ZERO));
    }

    @Test
    void addBalance_negativeAmount_throwsIllegalArgumentException() {
        authenticateAs(user);
        assertThrows(IllegalArgumentException.class,
            () -> walletService.addBalance(new BigDecimal("-10.00")));
    }

    @Test
    void addBalance_walletNotFound_throwsRuntimeException() {
        authenticateAs(user);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> walletService.addBalance(new BigDecimal("100.00")));
    }

    @Test
    void addBalance_validAmount_increasesAvailableBalance() {
        authenticateAs(user);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        walletService.addBalance(new BigDecimal("100.00"));

        assertEquals(new BigDecimal("600.00"), wallet.getAvailableBalance());
        verify(walletRepository).save(wallet);
    }

    // -----------------------------------------------------------------------
    // creditBalance
    // -----------------------------------------------------------------------

    @Test
    void creditBalance_walletNotFound_throwsRuntimeException() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> walletService.creditBalance(1L, new BigDecimal("100.00")));
    }

    @Test
    void creditBalance_validAmount_increasesAvailableBalance() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        walletService.creditBalance(1L, new BigDecimal("200.00"));

        assertEquals(new BigDecimal("700.00"), wallet.getAvailableBalance());
        verify(walletRepository).save(wallet);
    }

    @Test
    void creditBalance_addsExactAmount() {
        wallet.setAvailableBalance(BigDecimal.ZERO);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        walletService.creditBalance(1L, new BigDecimal("99.99"));

        assertEquals(new BigDecimal("99.99"), wallet.getAvailableBalance());
    }

    // -----------------------------------------------------------------------
    // deductBalance
    // -----------------------------------------------------------------------

    @Test
    void deductBalance_walletNotFound_throwsRuntimeException() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> walletService.deductBalance(1L, new BigDecimal("100.00")));
    }

    @Test
    void deductBalance_insufficientBalance_throwsRuntimeException() {
        wallet.setAvailableBalance(new BigDecimal("50.00"));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> walletService.deductBalance(1L, new BigDecimal("100.00")));
        assertTrue(ex.getMessage().contains("Insufficient balance"));
    }

    @Test
    void deductBalance_exactBalance_reducesToZero() {
        wallet.setAvailableBalance(new BigDecimal("100.00"));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        walletService.deductBalance(1L, new BigDecimal("100.00"));

        assertEquals(BigDecimal.ZERO, wallet.getAvailableBalance().stripTrailingZeros());
        verify(walletRepository).save(wallet);
    }

    @Test
    void deductBalance_validAmount_decreasesAvailableBalance() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        walletService.deductBalance(1L, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("400.00"), wallet.getAvailableBalance());
    }

    // -----------------------------------------------------------------------
    // addToFrozenBalance
    // -----------------------------------------------------------------------

    @Test
    void addToFrozenBalance_nullAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> walletService.addToFrozenBalance(1L, null));
    }

    @Test
    void addToFrozenBalance_zeroAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> walletService.addToFrozenBalance(1L, BigDecimal.ZERO));
    }

    @Test
    void addToFrozenBalance_walletNotFound_throwsRuntimeException() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> walletService.addToFrozenBalance(1L, new BigDecimal("100.00")));
    }

    @Test
    void addToFrozenBalance_insufficientAvailableBalance_throwsRuntimeException() {
        wallet.setAvailableBalance(new BigDecimal("50.00"));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> walletService.addToFrozenBalance(1L, new BigDecimal("100.00")));
        assertTrue(ex.getMessage().contains("Insufficient available balance"));
    }

    @Test
    void addToFrozenBalance_validAmount_movesFromAvailableToFrozen() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        walletService.addToFrozenBalance(1L, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("400.00"), wallet.getAvailableBalance());
        assertEquals(new BigDecimal("150.00"), wallet.getFrozenBalance());
        verify(walletRepository).save(wallet);
    }

    @Test
    void addToFrozenBalance_totalBalanceUnchanged() {
        BigDecimal totalBefore = wallet.getAvailableBalance().add(wallet.getFrozenBalance());
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        walletService.addToFrozenBalance(1L, new BigDecimal("100.00"));

        BigDecimal totalAfter = wallet.getAvailableBalance().add(wallet.getFrozenBalance());
        assertEquals(totalBefore, totalAfter);
    }

    // -----------------------------------------------------------------------
    // deductFromFrozenBalance
    // -----------------------------------------------------------------------

    @Test
    void deductFromFrozenBalance_nullAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> walletService.deductFromFrozenBalance(1L, null));
    }

    @Test
    void deductFromFrozenBalance_zeroAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> walletService.deductFromFrozenBalance(1L, BigDecimal.ZERO));
    }

    @Test
    void deductFromFrozenBalance_walletNotFound_throwsRuntimeException() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> walletService.deductFromFrozenBalance(1L, new BigDecimal("10.00")));
    }

    @Test
    void deductFromFrozenBalance_insufficientFrozenBalance_throwsRuntimeException() {
        wallet.setFrozenBalance(new BigDecimal("10.00"));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> walletService.deductFromFrozenBalance(1L, new BigDecimal("50.00")));
        assertTrue(ex.getMessage().contains("Insufficient frozen balance"));
    }

    @Test
    void deductFromFrozenBalance_validAmount_movesFromFrozenToAvailable() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        walletService.deductFromFrozenBalance(1L, new BigDecimal("50.00"));

        assertEquals(new BigDecimal("550.00"), wallet.getAvailableBalance());
        assertEquals(BigDecimal.ZERO, wallet.getFrozenBalance().stripTrailingZeros());
        verify(walletRepository).save(wallet);
    }

    @Test
    void deductFromFrozenBalance_totalBalanceUnchanged() {
        BigDecimal totalBefore = wallet.getAvailableBalance().add(wallet.getFrozenBalance());
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);

        walletService.deductFromFrozenBalance(1L, new BigDecimal("50.00"));

        BigDecimal totalAfter = wallet.getAvailableBalance().add(wallet.getFrozenBalance());
        assertEquals(totalBefore, totalAfter);
    }

    // -----------------------------------------------------------------------
    // transferFrozenToSellerBalance
    // -----------------------------------------------------------------------

    @Test
    void transfer_nullAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> walletService.transferFrozenToSellerBalance(1L, 2L, null));
    }

    @Test
    void transfer_zeroAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> walletService.transferFrozenToSellerBalance(1L, 2L, BigDecimal.ZERO));
    }

    @Test
    void transfer_fromWalletNotFound_throwsRuntimeException() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> walletService.transferFrozenToSellerBalance(1L, 2L, new BigDecimal("50.00")));
    }

    @Test
    void transfer_toWalletNotFound_throwsRuntimeException() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> walletService.transferFrozenToSellerBalance(1L, 2L, new BigDecimal("50.00")));
    }

    @Test
    void transfer_insufficientFrozenBalance_throwsRuntimeException() {
        wallet.setFrozenBalance(new BigDecimal("10.00"));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));

        Wallet sellerWallet = new Wallet();
        sellerWallet.setAvailableBalance(BigDecimal.ZERO);
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(sellerWallet));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> walletService.transferFrozenToSellerBalance(1L, 2L, new BigDecimal("50.00")));
        assertTrue(ex.getMessage().contains("Insufficient frozen balance"));
    }

    @Test
    void transfer_validAmount_deductsFrozenFromWinnerCreditsSeller() {
        Wallet sellerWallet = new Wallet();
        sellerWallet.setAvailableBalance(new BigDecimal("0.00"));
        sellerWallet.setFrozenBalance(BigDecimal.ZERO);

        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(sellerWallet));
        when(walletRepository.save(any())).thenReturn(null);

        Boolean result = walletService.transferFrozenToSellerBalance(1L, 2L, new BigDecimal("50.00"));

        assertTrue(result);
        assertEquals(BigDecimal.ZERO, wallet.getFrozenBalance().stripTrailingZeros());
        assertEquals(new BigDecimal("50.00"), sellerWallet.getAvailableBalance());
        verify(walletRepository, times(2)).save(any());
    }

    @Test
    void transfer_moneyNotCreatedOrDestroyed() {
        Wallet sellerWallet = new Wallet();
        sellerWallet.setAvailableBalance(new BigDecimal("100.00"));
        sellerWallet.setFrozenBalance(BigDecimal.ZERO);

        BigDecimal totalBefore = wallet.getAvailableBalance()
            .add(wallet.getFrozenBalance())
            .add(sellerWallet.getAvailableBalance());

        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(sellerWallet));
        when(walletRepository.save(any())).thenReturn(null);

        walletService.transferFrozenToSellerBalance(1L, 2L, new BigDecimal("50.00"));

        BigDecimal totalAfter = wallet.getAvailableBalance()
            .add(wallet.getFrozenBalance())
            .add(sellerWallet.getAvailableBalance());

        assertEquals(totalBefore, totalAfter);
    }

    // -----------------------------------------------------------------------
    // Concurrency tests
    // -----------------------------------------------------------------------

    @Test
    void creditBalance_concurrentCalls_eachCallReceivesCorrectWalletState() throws InterruptedException {
        int threads = 10;
        BigDecimal creditAmount = new BigDecimal("100.00");
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // Each thread gets its own wallet snapshot (simulates DB isolation per transaction)
        when(walletRepository.findByUserId(1L)).thenAnswer(inv -> {
            Wallet w = new Wallet();
            w.setAvailableBalance(new BigDecimal("500.00"));
            w.setFrozenBalance(BigDecimal.ZERO);
            return Optional.of(w);
        });
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    walletService.creditBalance(1L, creditAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        assertEquals(threads, successCount.get(), "All concurrent credit calls should succeed");
        assertEquals(0, errorCount.get(), "No errors expected");
    }

    @Test
    void addToFrozenBalance_concurrentCalls_allSucceedWithSufficientBalance() throws InterruptedException {
        int threads = 5;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // Each thread sees a wallet with enough balance
        when(walletRepository.findByUserId(1L)).thenAnswer(inv -> {
            Wallet w = new Wallet();
            w.setAvailableBalance(new BigDecimal("1000.00"));
            w.setFrozenBalance(BigDecimal.ZERO);
            return Optional.of(w);
        });
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    walletService.addToFrozenBalance(1L, new BigDecimal("100.00"));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        assertEquals(threads, successCount.get(), "All concurrent freeze calls should succeed");
        assertEquals(0, errorCount.get());
    }

    @Test
    void deductBalance_concurrentCalls_insufficientBalanceRejected() throws InterruptedException {
        int threads = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // All threads see a wallet with only enough for one deduction
        when(walletRepository.findByUserId(1L)).thenAnswer(inv -> {
            Wallet w = new Wallet();
            w.setAvailableBalance(new BigDecimal("50.00")); // less than the 100.00 being deducted
            w.setFrozenBalance(BigDecimal.ZERO);
            return Optional.of(w);
        });

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    walletService.deductBalance(1L, new BigDecimal("100.00")); // more than available
                    successCount.incrementAndGet();
                } catch (RuntimeException e) {
                    rejectedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        assertEquals(0, successCount.get(), "No calls should succeed — balance insufficient");
        assertEquals(threads, rejectedCount.get(), "All calls should be rejected");
    }
}
