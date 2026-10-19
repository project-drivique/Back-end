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
    void galleryHasAtMostThreePhotosWithThePrimaryFirst() {
        List<VehicleImage> images = List.of(
                image("https://x/2.jpg", false, 2),
                image("https://x/1.jpg", true, 1),
                image("https://x/3.jpg", false, 3),
                image("https://x/4.jpg", false, 4));
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getImages()).thenReturn(images);

        assertEquals(List.of("https://x/1.jpg", "https://x/2.jpg", "https://x/3.jpg"), VehicleMapper.galleryUrls(vehicle));
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
