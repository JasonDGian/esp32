package es.iesjandula.reaktor_projector_server.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import es.iesjandula.reaktor_projector_server.dtos.ActionDto;
import es.iesjandula.reaktor_projector_server.entities.Action;

public interface IActionRepository extends JpaRepository <Action, String>
{

	@Query(
			"""
			SELECT new es.iesjandula.reaktor_projector_server.dtos.ActionDto( ac.actionName ) FROM Action ac
			""")
	Page<ActionDto> findAllActionstoDto( Pageable pageable);
	
}
