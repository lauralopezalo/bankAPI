package com.ironhack.APIbank.services.impl;

import com.ironhack.APIbank.embeddable.Address;
import com.ironhack.APIbank.embeddable.Money;
import com.ironhack.APIbank.controllers.dto.accounts.CheckingDTO;
import com.ironhack.APIbank.controllers.dto.accounts.CreditCardDTO;
import com.ironhack.APIbank.controllers.dto.accounts.SavingsDTO;
import com.ironhack.APIbank.models.accounts.*;
import com.ironhack.APIbank.models.users.AccountHolder;
import com.ironhack.APIbank.models.users.Admin;
import com.ironhack.APIbank.models.users.Role;
import com.ironhack.APIbank.models.users.ThirdParty;
import com.ironhack.APIbank.repositories.accounts.AccountRepository;
import com.ironhack.APIbank.repositories.accounts.SavingsRepository;
import com.ironhack.APIbank.repositories.users.AccountHolderRepository;
import com.ironhack.APIbank.repositories.users.AdminRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AdminServiceImplTest {

    @Autowired
    private AccountHolderRepository accountHolderRepository;
    @Autowired
    private AdminServiceImpl adminServiceImpl;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private SavingsRepository savingsRepository;

    private AccountHolder accountHolder1, accountHolder2;
    private Savings savings;

    @BeforeEach
    void setUp() {
        accountHolder1 = new AccountHolder("Teresa", "123", "username", LocalDate.of(1970,02,20),
                new Address("Baker Street", "08080", "ciudad","país"),
                new Address("avenue", "08880", "ciudad2","país2"));
        accountHolder1.setRoles(List.of(new Role("ACCOUNT_HOLDER")));
        accountHolderRepository.save(accountHolder1);

        accountHolder2 = new AccountHolder("Marisa", "456", "username", LocalDate.of(2005,8,30),
                new Address("Evergreen Terrace", "08080", "ciudad","país"),
                new Address("avenue", "08880", "ciudad2","país2"));
        accountHolder2.setRoles(List.of(new Role("ACCOUNT_HOLDER")));
        accountHolderRepository.save(accountHolder2);

        savings = new Savings(accountHolder1,accountHolder2,"password", new BigDecimal("1000"), new BigDecimal("0.2"));
        savings.setBalance(new Money(new BigDecimal("100"), Currency.getInstance("USD")));
        savingsRepository.save(savings);
    }

    @AfterEach
    void tearDown() {
        accountHolderRepository.deleteAll();
        adminRepository.deleteAll();
    }


    @Test
    void addAdmin() throws Exception {
        Admin admin = new Admin();
        admin.setName("admin");
        admin.setPassword("password");
        admin.setUsername("username");
        Admin adminSaved = adminServiceImpl.addAdmin(admin);

        assertNotNull(adminSaved.getId());
        Admin adminInRepo = adminRepository.findById(adminSaved.getId()).orElse(null);
        assertNotNull(adminRepository.findById(adminSaved.getId()));
        assertEquals("admin", adminInRepo.getName());
        assertEquals("ADMIN", adminSaved.getRoles().get(0).getName());
    }

    @Test
    void addAccountHolder() throws Exception {
        AccountHolder holderInRepo = accountHolderRepository.findByName("Teresa").orElse(null);
        assertNotNull(holderInRepo.getId());
        assertEquals("Teresa", holderInRepo.getName());
        assertEquals("123", holderInRepo.getPassword());
        assertEquals("ACCOUNT_HOLDER", holderInRepo.getRoles().get(0).getName());
        assertEquals("Baker Street", holderInRepo.getPrimaryAddress().getStreet());
        assertEquals("avenue", holderInRepo.getMailAddress().getStreet());
    }

    @Test
    void addThirdParty() throws Exception {
        ThirdParty thirdParty = new ThirdParty("name","hashedKey","password","user456");
        ThirdParty thirdPartySaved = adminServiceImpl.addThirdParty(thirdParty);
        assertNotNull(thirdPartySaved.getId());
        assertEquals("hashedKey", thirdPartySaved.getHashedKey());
        assertEquals("THIRD_PARTY", thirdPartySaved.getRoles().get(0).getName());
        assertEquals("user456", thirdPartySaved.getUsername());
    }

    @Test
    void addChecking_StudentChecking() throws Exception {
        CheckingDTO checkingDTO = new CheckingDTO();
        checkingDTO.setPrimaryOwnerId(accountHolderRepository.findByName("Marisa").get().getId());
        checkingDTO.setSecondaryOwnerId(accountHolderRepository.findByName("Teresa").get().getId());
        checkingDTO.setSecretKey("secretKey");

        Account newAccount = adminServiceImpl.addChecking(checkingDTO);

        assertNotNull(newAccount.getId());
        assertTrue(newAccount instanceof StudentChecking);
        assertEquals("ACTIVE", ((StudentChecking) newAccount).getStatus().toString());
    }

    @Test
    void addChecking() throws Exception {
        CheckingDTO checkingDTO = new CheckingDTO();
        checkingDTO.setPrimaryOwnerId(accountHolderRepository.findByName("Teresa").get().getId());
        checkingDTO.setSecondaryOwnerId(accountHolderRepository.findByName("Marisa").get().getId());
        checkingDTO.setSecretKey("secretKey");

        Account newAccount = adminServiceImpl.addChecking(checkingDTO);

        assertNotNull(newAccount.getId());
        assertTrue(newAccount instanceof Checking);
        assertEquals("ACTIVE", ((Checking) newAccount).getStatus().toString());
    }

    @Test
    void addSavings_withPrimaryOwnerOnly_returnsSavingsAccount() {
        SavingsDTO savingsDTO = new SavingsDTO(
                accountHolderRepository.findByName("Teresa").get().getId(), null, "savingsSecretKey");

        Savings savings = adminServiceImpl.addSavings(savingsDTO);

        assertNotNull(savings.getId());
        assertNull(savings.getSecondaryOwner());
        assertEquals(Currency.getInstance("USD"), savings.getBalance().getCurrency());
        assertEquals("savingsSecretKey", savings.getSecretKey());
    }

    @Test
    void addCreditCard_withPrimaryOwnerOnly_returnsCreditCard() {
        AccountHolder primaryOwner = accountHolderRepository.findByName("Teresa").orElseThrow();
        CreditCardDTO creditCardDTO = new CreditCardDTO(primaryOwner.getId(), null, "password", new BigDecimal("1000"), new BigDecimal("0.2"));

        CreditCard creditCard = adminServiceImpl.addCreditCardAccount(creditCardDTO);

        assertNotNull(creditCard.getId());
        assertNull(creditCard.getSecondaryOwner());
        assertEquals(Currency.getInstance("USD"), creditCard.getBalance().getCurrency());
        assertEquals(new BigDecimal("1000"), creditCard.getCreditLimit());
        assertEquals(new BigDecimal("0.2"), creditCard.getInterestRate());
    }

    @Test
    void getAccountBalance_savingsAccount_returnsUpdatedBalance() {
        Optional<Money> updatedBalance = Optional.ofNullable(savingsRepository.findById(savings.getId()).get().getBalance());
        assertNotNull(updatedBalance);
        assertEquals(new BigDecimal("100.00"), updatedBalance.get().getAmount());
    }

    @Test
    public void testGetAccountBalance_creditCard_returnsUpdatedBalance() {
        Account account = new Checking();
        account.setPrimaryOwner(accountHolder1);
        account.setBalance(new Money(new BigDecimal("1000")));
        account.setId(1L);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("username");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        AccountRepository accountHolderRepository = mock(AccountRepository.class);
        when(accountHolderRepository.findById(1L)).thenReturn(Optional.of(account));

        Money balance = account.getBalance();
        assertEquals(new Money(new BigDecimal("1000")), balance);
    }

    @Test
    void updateAccountBalance_savingsAccount_returnsUpdatedAccount() {
        Money newBalance = new Money(new BigDecimal("200"), Currency.getInstance("USD"));
        Account updatedAccount = adminServiceImpl.updateAccountBalance(savings.getId(), newBalance);
        assertNotNull(updatedAccount);
        assertEquals(newBalance.getAmount(), updatedAccount.getBalance().getAmount());
    }

    @Test
    void deleteAccount_existingAccount_deletesAccount() {
        adminServiceImpl.deleteAccount(savings.getId());
        assertFalse(accountHolderRepository.existsById(savings.getId()));
    }

    @Test
    void deleteAccount_nonexistentAccount_throwsException() {
        Long nonexistentAccountId = Long.MAX_VALUE;
        assertThrows(ResponseStatusException.class, () -> adminServiceImpl.deleteAccount(nonexistentAccountId));
    }

}