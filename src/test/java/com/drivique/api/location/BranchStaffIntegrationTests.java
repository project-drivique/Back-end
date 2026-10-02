package com.drivique.api.location;

import com.drivique.api.DatabaseHealthTestSupport;

import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev") @AutoConfigureMockMvc
class BranchStaffIntegrationTests extends DatabaseHealthTestSupport {
 @Autowired MockMvc mvc; @Autowired BranchUserRepository assignments; @Autowired BranchRepository branches; @Autowired CityRepository cities; @Autowired DepartmentRepository departments; @Autowired UserRepository users; @Autowired RoleRepository roles;
 @BeforeEach void reset() { resetFleetTables(); resetLocationTables(); resetIamTables(); }
 @Test @WithMockUser(roles="BRANCH_ADMIN") void assignsListsAndRemovesOperationalStaff() throws Exception {
  Branch branch=branch(); User employee=user("EMPLOYEE", "employee@drivique.com");
  mvc.perform(post("/api/v1/branches/{branch}/staff/{user}",branch.getId(),employee.getId()).contextPath("/api")).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/branches/{branch}/staff",branch.getId()).contextPath("/api")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].email").value("employee@drivique.com"));
  mvc.perform(post("/api/v1/branches/{branch}/staff/{user}",branch.getId(),employee.getId()).contextPath("/api")).andExpect(status().isConflict());
  mvc.perform(delete("/api/v1/branches/{branch}/staff/{user}",branch.getId(),employee.getId()).contextPath("/api")).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/branches/{branch}/staff",branch.getId()).contextPath("/api")).andExpect(jsonPath("$.length()").value(0));
 }
 @Test @WithMockUser(roles="BRANCH_ADMIN") void rejectsCustomerAndUnknownAssignments() throws Exception { Branch b=branch(); User customer=user("CUSTOMER","customer@drivique.com"); mvc.perform(post("/api/v1/branches/{branch}/staff/{user}",b.getId(),customer.getId()).contextPath("/api")).andExpect(status().isConflict()); }
 @Test void anonymousCannotManageStaff() throws Exception { Branch b=branch(); User e=user("EMPLOYEE","employee@drivique.com"); mvc.perform(post("/api/v1/branches/{branch}/staff/{user}",b.getId(),e.getId()).contextPath("/api")).andExpect(status().isUnauthorized()); }
 private Branch branch(){ Department d=departments.saveAndFlush(new Department("Cundinamarca")); City c=cities.saveAndFlush(new City(d,"Bogotá",true,true)); return branches.saveAndFlush(new Branch("Sede", "Calle 1", c, "123", LocalTime.of(8,0),LocalTime.of(18,0),true)); }
 private User user(String roleCode,String email){ Role r=roles.saveAndFlush(new Role(roleCode,roleCode,roleCode,true)); User u=new User("Test","User",email,"hash"); u.setRoles(Set.of(r)); return users.saveAndFlush(u); }
}
