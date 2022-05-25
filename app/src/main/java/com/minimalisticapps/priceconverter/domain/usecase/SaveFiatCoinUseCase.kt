package com.minimalisticapps.priceconverter.domain.usecase

import com.minimalisticapps.priceconverter.domain.repo.PriceConverterRepository
import com.minimalisticapps.priceconverter.room.entities.FiatCoinExchange
import javax.inject.Inject

class SaveFiatCoinUseCase @Inject constructor(
    private val priceConverterRepository: PriceConverterRepository
) {
    suspend operator fun invoke(fiatCoinExchange: FiatCoinExchange) =
        priceConverterRepository.saveFiatCoin(fiatCoinExchange)

    suspend operator fun invoke(fiatCoinList: List<FiatCoinExchange>) =
        priceConverterRepository.saveFiatCoinList(fiatCoinList)
}