package com.project.byeoldori.community.common.util

import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import javax.imageio.ImageIO

object ImageHeaderReader {
    /**
     * 픽셀을 디코딩하지 않고 헤더에서 가로·세로 크기만 읽는다.
     * 압축률이 높은 거대 이미지(픽셀 폭탄)는 전체 디코딩만으로 힙을 고갈시키므로,
     * 크기 검사는 반드시 이 값으로 디코딩 "전에" 해야 한다.
     * 읽을 수 있는 리더가 없거나 헤더가 깨졌으면 null.
     */
    fun readSize(file: MultipartFile): Pair<Int, Int>? {
        file.inputStream.use { raw ->
            val iis = ImageIO.createImageInputStream(raw) ?: return null
            iis.use {
                val readers = ImageIO.getImageReaders(iis)
                if (!readers.hasNext()) return null
                val reader = readers.next()
                return try {
                    reader.setInput(iis, true, true)
                    reader.getWidth(0) to reader.getHeight(0)
                } catch (e: IOException) {
                    null
                } catch (e: IndexOutOfBoundsException) {
                    null
                } finally {
                    reader.dispose()
                }
            }
        }
    }
}
