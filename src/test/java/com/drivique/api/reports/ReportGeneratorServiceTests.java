package com.drivique.api.reports;

import com.drivique.api.dto.AdministrativeReportTypeResponseDTO;
import com.drivique.api.dto.GenerateReportRequestDTO;
import com.drivique.api.dto.GeneratedReportResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.AdministrativeReportType;
import com.drivique.api.model.GeneratedReport;
import com.drivique.api.model.RentalContract;
import com.drivique.api.model.User;
import com.drivique.api.model.Vehicle;
import com.drivique.api.model.VehicleBrand;
import com.drivique.api.model.VehicleCategory;
import com.drivique.api.model.VehicleStatus;
import com.drivique.api.repository.AdministrativeReportTypeRepository;
import com.drivique.api.repository.GeneratedReportRepository;
import com.drivique.api.repository.RentalContractRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.repository.VehicleMaintenanceRepository;
import com.drivique.api.repository.VehicleRepository;
import com.drivique.api.service.FileStorageService;
import com.drivique.api.service.ReportGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGeneratorServiceTests {

    @Mock
    private AdministrativeReportTypeRepository reportTypeRepository;

    @Mock
    private GeneratedReportRepository generatedReportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RentalContractRepository rentalContractRepository;

    @Mock
    private VehicleMaintenanceRepository maintenanceRepository;

    @Mock
    private FileStorageService fileStorageService;

    private ReportGeneratorService reportGeneratorService;

    private User adminUser;
    private AdministrativeReportType occupancyType;
    private AdministrativeReportType revenueType;

    @BeforeEach
    void setUp() {
        reportGeneratorService = new ReportGeneratorService(
                reportTypeRepository,
                generatedReportRepository,
                userRepository,
                vehicleRepository,
                rentalContractRepository,
                maintenanceRepository,
                fileStorageService
        );

        adminUser = new User("Admin", "User", "admin@drivique.com", "secret");
        adminUser.setId(UUID.randomUUID());

        occupancyType = new AdministrativeReportType("FLEET_OCCUPANCY", "Ocupación de Flota", "Reporte de flota", true);
        revenueType = new AdministrativeReportType("REVENUE_SUMMARY", "Resumen de Ingresos", "Reporte financiero", true);
    }

    @Test
    void getReportTypes_ReturnsActiveTypes() {
        when(reportTypeRepository.count()).thenReturn(2L);
        when(reportTypeRepository.findByActiveTrueOrderByCodeAsc()).thenReturn(List.of(occupancyType, revenueType));

        List<AdministrativeReportTypeResponseDTO> result = reportGeneratorService.getReportTypes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("FLEET_OCCUPANCY");
        assertThat(result.get(1).code()).isEqualTo("REVENUE_SUMMARY");
    }

    @Test
    void generateReport_FleetOccupancy_PdfFormat_Success() {
        GenerateReportRequestDTO request = new GenerateReportRequestDTO(
                "FLEET_OCCUPANCY",
                "PDF",
                LocalDate.now().minusDays(30),
                LocalDate.now()
        );

        VehicleBrand brand = new VehicleBrand("Toyota");
        VehicleCategory category = new VehicleCategory("SUV", new BigDecimal("150000"), new BigDecimal("1000000"));
        VehicleStatus status = new VehicleStatus("AVAILABLE", "Disponible", true);
        Vehicle vehicle = new Vehicle("ABC-123", "VIN123", brand, category, null, null, status, null,
                "Toyota RAV4", (short) 2024, "Blanco", (short) 5, (short) 5, 500, 10000, new BigDecimal("200000"), null, true);

        when(reportTypeRepository.count()).thenReturn(2L);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("admin@drivique.com")).thenReturn(Optional.of(adminUser));
        when(reportTypeRepository.findByCodeIgnoreCase("FLEET_OCCUPANCY")).thenReturn(Optional.of(occupancyType));
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle));
        when(fileStorageService.storeBytes(any(byte[].class), any(String.class), eq("reports")))
                .thenReturn("/uploads/reports/fleet_occupancy.pdf");
        when(generatedReportRepository.save(any(GeneratedReport.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedReportResponseDTO response = reportGeneratorService.generateReport(request, "admin@drivique.com");

        assertThat(response).isNotNull();
        assertThat(response.reportTypeCode()).isEqualTo("FLEET_OCCUPANCY");
        assertThat(response.format()).isEqualTo("PDF");
        assertThat(response.fileUrl()).isEqualTo("/uploads/reports/fleet_occupancy.pdf");
        verify(generatedReportRepository).save(any(GeneratedReport.class));
    }

    @Test
    void generateReport_RevenueSummary_ExcelFormat_Success() {
        GenerateReportRequestDTO request = new GenerateReportRequestDTO(
                "REVENUE_SUMMARY",
                "EXCEL",
                LocalDate.now().minusDays(30),
                LocalDate.now()
        );

        when(reportTypeRepository.count()).thenReturn(2L);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("admin@drivique.com")).thenReturn(Optional.of(adminUser));
        when(reportTypeRepository.findByCodeIgnoreCase("REVENUE_SUMMARY")).thenReturn(Optional.of(revenueType));
        when(rentalContractRepository.findAll()).thenReturn(List.of());
        when(fileStorageService.storeBytes(any(byte[].class), any(String.class), eq("reports")))
                .thenReturn("/uploads/reports/revenue_summary.xlsx");
        when(generatedReportRepository.save(any(GeneratedReport.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedReportResponseDTO response = reportGeneratorService.generateReport(request, "admin@drivique.com");

        assertThat(response).isNotNull();
        assertThat(response.reportTypeCode()).isEqualTo("REVENUE_SUMMARY");
        assertThat(response.format()).isEqualTo("EXCEL");
        assertThat(response.fileUrl()).isEqualTo("/uploads/reports/revenue_summary.xlsx");
    }

    @Test
    void generateReport_CsvFormat_Success() {
        GenerateReportRequestDTO request = new GenerateReportRequestDTO(
                "FLEET_OCCUPANCY",
                "CSV",
                null,
                null
        );

        when(reportTypeRepository.count()).thenReturn(2L);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("admin@drivique.com")).thenReturn(Optional.of(adminUser));
        when(reportTypeRepository.findByCodeIgnoreCase("FLEET_OCCUPANCY")).thenReturn(Optional.of(occupancyType));
        when(vehicleRepository.findAll()).thenReturn(List.of());
        when(fileStorageService.storeBytes(any(byte[].class), any(String.class), eq("reports")))
                .thenReturn("/uploads/reports/fleet_occupancy.csv");
        when(generatedReportRepository.save(any(GeneratedReport.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedReportResponseDTO response = reportGeneratorService.generateReport(request, "admin@drivique.com");

        assertThat(response).isNotNull();
        assertThat(response.format()).isEqualTo("CSV");
    }

    @Test
    void getGeneratedReports_ReturnsList() {
        GeneratedReport r1 = new GeneratedReport(occupancyType, "PDF", adminUser, "{}", "/file1.pdf", "COMPLETED");
        GeneratedReport r2 = new GeneratedReport(revenueType, "EXCEL", adminUser, "{}", "/file2.xlsx", "COMPLETED");

        when(generatedReportRepository.findAllByOrderByGeneratedAtDesc()).thenReturn(List.of(r1, r2));

        List<GeneratedReportResponseDTO> result = reportGeneratorService.getGeneratedReports(null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).reportTypeCode()).isEqualTo("FLEET_OCCUPANCY");
        assertThat(result.get(1).reportTypeCode()).isEqualTo("REVENUE_SUMMARY");
    }

    @Test
    void getReportById_WhenFound_ReturnsDTO() {
        UUID id = UUID.randomUUID();
        GeneratedReport report = new GeneratedReport(occupancyType, "PDF", adminUser, "{}", "/file.pdf", "COMPLETED");
        report.setId(id);

        when(generatedReportRepository.findById(id)).thenReturn(Optional.of(report));

        GeneratedReportResponseDTO result = reportGeneratorService.getReportById(id);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    void getReportById_WhenNotFound_ThrowsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(generatedReportRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportGeneratorService.getReportById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reporte generado no encontrado");
    }
}
