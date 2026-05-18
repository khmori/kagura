package com.khmori.kagura.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.khmori.kagura.entity.Compound;

public interface CompoundRepository extends JpaRepository<Compound, Integer> {
    Optional<Compound> findByCompound(String compound);
}