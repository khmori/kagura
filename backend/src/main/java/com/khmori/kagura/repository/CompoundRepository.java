package com.khmori.kagura.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.khmori.kagura.entity.Compound;

public interface CompoundRepository extends JpaRepository<Compound, Integer> {
}