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
            ObservationSite(name = "충북대 잔디밭", latitude = 36.63124716519853, longitude = 127.45305824381116),
            ObservationSite(name = "안성 천문대", latitude = 36.955725028582066, longitude = 127.2338729230954),
            ObservationSite(name = "서울 시립 천문대", latitude = 37.54630335580719, longitude = 127.10603612529096),
            ObservationSite(name = "용인 어린이 천문대", latitude = 37.24943933882984, longitude = 127.12877298650575),
            ObservationSite(name = "영월 별마로 천문대", latitude = 37.19832470053874, longitude = 128.4867852180296),
            ObservationSite(name = "보현산 천문대", latitude = 36.16391809752477, longitude = 128.97626320349622),
            ObservationSite(name = "밀양 아리랑 우주천문대", latitude = 35.50258172296012, longitude = 128.76108149122422),
            ObservationSite(name = "무등산", latitude = 35.12436106871411, longitude = 127.0090373171599),
            ObservationSite(name = "인천 수목원", latitude = 37.461861121050596, longitude = 126.75349684625436),
            ObservationSite(name = "서귀포 천문 과학문화관", latitude = 33.28883494400285, longitude = 126.46041561281157),
            ObservationSite(name = "설악산 국립공원", latitude = 38.13584704340961, longitude = 128.41252964009772)
        )

        val newSites = sampleSites.filter { site ->
            !siteRepository.existsByNameAndLatitudeAndLongitude(site.name, site.latitude, site.longitude)
        }

        siteRepository.saveAll(newSites)

        println("새로 삽입된 관측지 수: ${newSites.size}")
    }
}
