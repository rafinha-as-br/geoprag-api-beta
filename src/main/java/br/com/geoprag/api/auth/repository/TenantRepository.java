package br.com.geoprag.api.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.geoprag.api.auth.domain.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, String> {
}
