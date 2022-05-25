package com.minimalisticapps.priceconverter.domain.usecase

import com.minimalisticapps.priceconverter.domain.repo.PriceConverterRepository
import com.minimalisticapps.priceconverter.room.entities.BitPayCoinWithFiatCoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetFiatCoinsUseCase @Inject constructor(
    private val priceConverterRepository: PriceConverterRepository
) {
    var list: List<BitPayCoinWithFiatCoin> = emptyList()
    suspend operator fun invoke(): Flow<Pair<Boolean, List<BitPayCoinWithFiatCoin>>> {
        return callbackFlow {
            priceConverterRepository.getFiatCoins().collect {
                if (list != it) {
                    list = it
                    trySend(Pair(true, list))
                } else {
                    trySend(Pair(false, emptyList<BitPayCoinWithFiatCoin>()))
                }
            }

            awaitClose {
                cancel()
            }
        }
            .flowOn(Dispatchers.IO)
    }
}