package com.project.byeoldori.observationsites.init

import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class ObservationSiteSeeder(
    private val siteRepository: ObservationSiteRepository
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        val sampleSites = listOf(
            ObservationSite(name = "우암산 전망대", latitude = 36.65003430206848, longitude = 127.50494706148991),
            ObservationSite(name = "매봉산", latitude = 36.62337885771579, longitude = 127.4753990979296),
            ObservationSite(name = "충북대 대운동장", latitude = 36.62599134299997, longitude = 127.46185397919821),
            ObservationSite(name = "장구봉 공원", latitude = 36.623385304303675, longitude = 127.44575674344435),
            ObservationSite(name = "충북대 잔디밭", latitude = 36.63124716519853, longitude = 127.45305824381116)
        )

        val newSites = sampleSites.filter { site ->
            !siteRepository.existsByNameAndLatitudeAndLongitude(site.name, site.latitude, site.longitude)
        }

        siteRepository.saveAll(newSites)

        println("새로 삽입된 관측지 수: ${newSites.size}")
    }
}
