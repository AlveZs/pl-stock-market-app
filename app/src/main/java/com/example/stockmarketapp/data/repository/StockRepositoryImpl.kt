package com.example.stockmarketapp.data.repository

import com.example.stockmarketapp.data.csv.CSVParser
import com.example.stockmarketapp.data.csv.CompanyListingsParser
import com.example.stockmarketapp.data.local.StockDatabase
import com.example.stockmarketapp.data.mapper.toCompanyListing
import com.example.stockmarketapp.data.mapper.toCompanyListingEntity
import com.example.stockmarketapp.data.remote.StockApi
import com.example.stockmarketapp.domain.model.CompanyListing
import com.example.stockmarketapp.domain.repository.StockRepository
import com.example.stockmarketapp.util.Resource
import com.opencsv.CSVReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepositoryImpl @Inject constructor(
    val api: StockApi,
    val db: StockDatabase,
    val companyListingsParser: CSVParser<CompanyListing>
): StockRepository {

    private val dao = db.dao

    override suspend fun getCompanyListings(
        fetchFromRemote: Boolean,
        query: String
    ): Flow<Resource<List<CompanyListing>>> {
        return flow {
            emit(Resource.Loading(true))
            val localListings = dao.searchCompanyListing(query)
            emit(Resource.Success(
                data = localListings.map { it.toCompanyListing() }
            ))

            val isDbEmpty = localListings.isEmpty() && query.isBlank()
            val shouldJustLoadFromCache = !isDbEmpty && !fetchFromRemote
            if(shouldJustLoadFromCache) {
              emit(Resource.Loading(false))
              return@flow
            }
            val remoteListings = try {
                val response = api.getListings()
                companyListingsParser.parse(response.byteStream())
            } catch (e: IOException) {
                e.printStackTrace()
                emit(Resource.Error("Couldn't load data"))
                null
            } catch (e: HttpException) {
                e.printStackTrace()
                emit(Resource.Error("Couldn't load data"))
                null
            }

            remoteListings?.let { listings ->
                dao.clearCompanyListings()
                dao.insertCompanyListings(
                    listings.map { it.toCompanyListingEntity() }
                )
                emit(Resource.Success(
                    data = dao
                        .searchCompanyListing("")
                        .map { it.toCompanyListing() }
                ))
                emit(Resource.Loading(false))
            }
        }
    }
}