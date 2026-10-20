package br.com.geoprag.api.auth.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.geoprag.api.auth.domain.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    /**
     * Carrega o tenant com {@code SELECT … FOR UPDATE}: serializa operações que dependem do conjunto de pontos do
     * tenant (ex.: gerar o próximo {@code identificador}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> findByIdParaAtualizar(@Param("id") String id);
}
