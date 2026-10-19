package com.drivique.api.mapper;

import com.drivique.api.model.Vehicle;
import com.drivique.api.model.VehicleImage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VehicleGalleryTests {

    private static VehicleImage image(String url, boolean primary, int order) {
        VehicleImage image = mock(VehicleImage.class);
        when(image.getUrl()).thenReturn(url);
        when(image.isPrimary()).thenReturn(primary);
        when(image.getSortOrder()).thenReturn((short) order);
        return image;
    }

    @Test
    void galleryHasAtMostEightPhotosWithThePrimaryFirst() {
        List<VehicleImage> images = new java.util.ArrayList<>();
        images.add(image("https://x/2.jpg", false, 2));
        images.add(image("https://x/1.jpg", true, 1));
        for (int i = 3; i <= 9; i++) {
            images.add(image("https://x/" + i + ".jpg", false, i));
        }
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getImages()).thenReturn(images);

        assertEquals(List.of("https://x/1.jpg", "https://x/2.jpg", "https://x/3.jpg", "https://x/4.jpg", "https://x/5.jpg", "https://x/6.jpg", "https://x/7.jpg", "https://x/8.jpg"), VehicleMapper.galleryUrls(vehicle));
    }

    @Test
    void fallsBackToTheMainPhotoWhenThereIsNoGallery() {
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getImages()).thenReturn(List.of());
        when(vehicle.getMainImageUrl()).thenReturn("https://x/main.jpg");

        assertEquals(List.of("https://x/main.jpg"), VehicleMapper.galleryUrls(vehicle));
    }

    @Test
    void returnsNoPhotosWhenTheVehicleHasNone() {
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getImages()).thenReturn(List.of());

        assertEquals(List.of(), VehicleMapper.galleryUrls(vehicle));
    }
}
