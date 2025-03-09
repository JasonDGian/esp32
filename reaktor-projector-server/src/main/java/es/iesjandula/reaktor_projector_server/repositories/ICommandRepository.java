package es.iesjandula.reaktor_projector_server.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.iesjandula.reaktor_projector_server.dtos.ActionDto;
import es.iesjandula.reaktor_projector_server.dtos.CommandDto;
import es.iesjandula.reaktor_projector_server.entities.Action;
import es.iesjandula.reaktor_projector_server.entities.Command;
import es.iesjandula.reaktor_projector_server.entities.ProjectorModel;
import es.iesjandula.reaktor_projector_server.entities.ids.CommandId;

public interface ICommandRepository extends JpaRepository<Command, CommandId >
{
    @Query("SELECT new es.iesjandula.reaktor_projector_server.dtos.CommandDto( " +
            "c.modelName.modelName, c.action.actionName, c.command ) " +
            "FROM Command c " +
            "WHERE c.modelName.modelName = :modelName")
     List<CommandDto> findCommandsByModel(@Param("modelName") String modelName);
    
    Optional<Command> findByModelNameAndAction(ProjectorModel modelName, Action action);
    
    
	@Query(
			"""
			SELECT COUNT(*) 
			FROM Command cmd 
			WHERE LOWER(cmd.modelName.modelName) = LOWER(:modelname) 
			""")
	public Integer countModelCommands( @Param("modelname") String modelname );
	
	@Query(
			"""
			SELECT new es.iesjandula.reaktor_projector_server.dtos.CommandDto(
			c.modelName.modelName, c.action.actionName, c.command )
			FROM Command c 
			WHERE ( :modelName = '' OR :modelName IS NULL OR c.modelName.modelName = :modelName) 
			AND ( :action = '' OR :action IS NULL OR c.action.actionName = :action)
			""")
	Page<CommandDto> findAllCommandsPage( 
			Pageable pageable, 
			@Param("modelName") String modelName,
			@Param("action") String action
			);
	
}
