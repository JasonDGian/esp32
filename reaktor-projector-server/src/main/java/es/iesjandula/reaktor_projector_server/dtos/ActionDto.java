package es.iesjandula.reaktor_projector_server.dtos;

import es.iesjandula.reaktor_projector_server.entities.Action;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionDto
{

	private String actionName;
	
	
	public ActionDto ( Action actionEntity ){
		this.setActionName(actionEntity.getActionName());
	}
	
}

