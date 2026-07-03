package com.example.finance_app.bank.dto.request;

import com.example.finance_app.bank.enums.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateAccountRequest {

    @NotNull(message = "Currency is required")
    private Currency currency;

    @Size(max = 100, message = "Account name must not exceed 100 characters")
    private String accountName;
}
