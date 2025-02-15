package es.iesjandula.reaktor_projector_server.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import es.iesjandula.reaktor_projector_server.dtos.SimplifiedServerEventDto;
import es.iesjandula.reaktor_projector_server.entities.Projector;
import es.iesjandula.reaktor_projector_server.entities.ServerEvent;

public interface IServerEventRepository extends JpaRepository<ServerEvent, Long>
{
	
	@Query("SELECT new es.iesjandula.reaktor_projector_server.dtos.SimplifiedServerEventDto( se.eventId, se.command.command, se.actionStatus ) "
			+ "FROM ServerEvent se "
			+ "WHERE se.projector = :projector "
			+ "AND se.actionStatus LIKE :actionStatus "
			+ "ORDER BY se.dateTime DESC")
	List<SimplifiedServerEventDto> findMostRecentCommandOpen(Projector projector, String actionStatus);
}



