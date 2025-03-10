package es.iesjandula.reaktor_projector_server.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.iesjandula.reaktor_projector_server.dtos.SimplifiedServerEventDto;
import es.iesjandula.reaktor_projector_server.dtos.TableServerEventDto;
import es.iesjandula.reaktor_projector_server.entities.Projector;
import es.iesjandula.reaktor_projector_server.entities.ServerEvent;

public interface IServerEventRepository extends JpaRepository<ServerEvent, Long>
{

	@Query("SELECT new es.iesjandula.reaktor_projector_server.dtos.SimplifiedServerEventDto( se.eventId, se.command.command, se.actionStatus ) "
			+ "FROM ServerEvent se " + "WHERE se.projector = :projector " + "AND se.actionStatus = :actionStatus "
			+ "ORDER BY se.dateTime DESC")
	List<SimplifiedServerEventDto> findMostRecentCommandOpen(Projector projector, String actionStatus);

	@Query("SELECT se FROM ServerEvent se WHERE se.projector = :projector AND se.actionStatus = :actionStatus ORDER BY se.dateTime DESC")
	List<ServerEvent> findMostRecentServerEventsOpen(Projector projector, String actionStatus);

	@Query("""
			SELECT new es.iesjandula.reaktor_projector_server.dtos.TableServerEventDto(
			se.eventId,
			c.action.actionName,
			p.model.modelName,
			p.classroom.classroomName,
			p.classroom.floor.floorName,
			se.user,
			se.dateTime,
			se.actionStatus
			)
			FROM ServerEvent se
			JOIN se.command c
			JOIN se.projector p
			ORDER BY se.dateTime DESC
			""")
	List<TableServerEventDto> getTableServerEventDtoList();

	@Query("""
			SELECT new es.iesjandula.reaktor_projector_server.dtos.TableServerEventDto(
			se.eventId,
			c.action.actionName,
			p.model.modelName,
			p.classroom.classroomName,
			p.classroom.floor.floorName,
			se.user,
			se.dateTime,
			se.actionStatus
			)
			FROM ServerEvent se
			JOIN se.command c
			JOIN se.projector p
			WHERE (:classroom = '' OR :classroom IS NULL OR p.classroom.classroomName = :classroom)
			AND (:floor = '' OR :floor IS NULL OR p.classroom.floor.floorName = :floor)
			AND (:model = '' OR :model IS NULL OR p.model.modelName = :model)
			AND (:actionStatus = '' OR :actionStatus IS NULL OR se.actionStatus = :actionStatus)
			ORDER BY se.dateTime DESC
			""")
	Page<TableServerEventDto> getTableServerEventDtoPage(Pageable pageable, @Param("classroom") String classroom,
			@Param("floor") String floor, @Param("model") String model, @Param("actionStatus") String actionStatus);

	@Query("""
			SELECT COUNT(*)
			FROM ServerEvent se
			WHERE (se.actionStatus = :actionStatus)
			""")
	Long getNumberOfEventsCountByStatus(@Param("actionStatus") String actionStatus);

}
