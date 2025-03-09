package es.iesjandula.reaktor_projector_server.rest;

import java.io.IOException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import es.iesjandula.reaktor_projector_server.dtos.ActionDto;
import es.iesjandula.reaktor_projector_server.dtos.ClassroomDto;
import es.iesjandula.reaktor_projector_server.dtos.CommandDto;
import es.iesjandula.reaktor_projector_server.dtos.EventFilterObject;
import es.iesjandula.reaktor_projector_server.dtos.FloorDto;
import es.iesjandula.reaktor_projector_server.dtos.GeneralCountOverviewDto;
import es.iesjandula.reaktor_projector_server.dtos.ModelOverviewDto;
import es.iesjandula.reaktor_projector_server.dtos.ProjectorDto;
import es.iesjandula.reaktor_projector_server.dtos.ProjectorInfoDto;
import es.iesjandula.reaktor_projector_server.dtos.ProjectorModelDto;
import es.iesjandula.reaktor_projector_server.dtos.ResponseDto;
import es.iesjandula.reaktor_projector_server.dtos.RichResponseDto;
import es.iesjandula.reaktor_projector_server.dtos.ServerEventBatchDto;
import es.iesjandula.reaktor_projector_server.dtos.ServerEventOverviewDto;
import es.iesjandula.reaktor_projector_server.dtos.SimplifiedServerEventDto;
import es.iesjandula.reaktor_projector_server.dtos.TableServerEventDto;
import es.iesjandula.reaktor_projector_server.entities.Action;
import es.iesjandula.reaktor_projector_server.entities.Classroom;
import es.iesjandula.reaktor_projector_server.entities.Command;
import es.iesjandula.reaktor_projector_server.entities.Floor;
import es.iesjandula.reaktor_projector_server.entities.Projector;
import es.iesjandula.reaktor_projector_server.entities.ProjectorModel;
import es.iesjandula.reaktor_projector_server.entities.ServerEvent;
import es.iesjandula.reaktor_projector_server.entities.ids.CommandId;
import es.iesjandula.reaktor_projector_server.entities.ids.ProjectorId;
import es.iesjandula.reaktor_projector_server.parsers.interfaces.IClassroomParser;
import es.iesjandula.reaktor_projector_server.parsers.interfaces.ICommandParser;
import es.iesjandula.reaktor_projector_server.parsers.interfaces.IProjectorModelParser;
import es.iesjandula.reaktor_projector_server.parsers.interfaces.IProjectorParser;
import es.iesjandula.reaktor_projector_server.repositories.IActionRepository;
import es.iesjandula.reaktor_projector_server.repositories.IClassroomRepository;
import es.iesjandula.reaktor_projector_server.repositories.ICommandRepository;
import es.iesjandula.reaktor_projector_server.repositories.IFloorRepository;
import es.iesjandula.reaktor_projector_server.repositories.IProjectorModelRepository;
import es.iesjandula.reaktor_projector_server.repositories.IProjectorRepository;
import es.iesjandula.reaktor_projector_server.repositories.IServerEventRepository;
import es.iesjandula.reaktor_projector_server.services.ProjectorRemovalHandler;
import es.iesjandula.reaktor_projector_server.utils.Constants;
import es.iesjandula.reaktor_projector_server.utils.ProjectorServerException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ProjectorController
{

	@Autowired
	IClassroomParser classroomParser;

	@Autowired
	ICommandParser commandsParser;

	@Autowired
	IProjectorParser projectorParser;

	@Autowired
	IProjectorModelParser projectorModelsParser;

	@Autowired
	IServerEventRepository serverEventRepository;

	@Autowired
	IProjectorRepository projectorRepository;

	@Autowired
	IProjectorModelRepository projectorModelRepository;

	@Autowired
	ICommandRepository commandRepository;

	@Autowired
	IActionRepository actionRepositories;

	@Autowired
	IClassroomRepository classroomRepository;

	@Autowired
	IFloorRepository floorRepository;

	@Autowired
	ProjectorRemovalHandler projectorRemovalHandler;

	// ----------------------------- UTILITY METHODS -------------------------------

	/**
	 * Validates the uploaded CSV file.
	 * <p>
	 * Ensures the uploaded file is not empty and has a valid content type (CSV). If
	 * validation fails, a {@link ProjectorServerException} is thrown with an
	 * appropriate error code and message.
	 * </p>
	 *
	 * @param file The uploaded {@link MultipartFile} to be validated.
	 * @throws ProjectorServerException if the file is empty or has an invalid
	 *                                  content type.
	 */
	private void validateFile(MultipartFile file) throws ProjectorServerException
	{
		// Check if the file is empty
		if (file.isEmpty())
		{
			log.warn("File validation failed: Received an empty CSV file.");
			throw new ProjectorServerException(490, "ERROR: Empty CSV file received.");
		}

		String contentType = file.getContentType();

		// Check if the file content type is valid for CSV files
		if (contentType == null || !contentType.equals("text/csv"))
		{
			log.warn("File validation failed: Unsupported file format received. Expected 'text/csv', but got: {}",
					contentType);
			throw new ProjectorServerException(498, "ERROR: Unsupported format. Expected format CSV.");
		}

		log.info("File validation successful: Received a valid CSV file.");
	}

	/**
	 * Creates a server event for a projector with the specified model, classroom,
	 * and action.
	 * 
	 * This method retrieves the necessary entities (projector model, classroom,
	 * projector, and action) from the database, validates the inputs, and creates a
	 * new server event with the appropriate details.
	 * 
	 * @param projectorModelName The model name of the projector.
	 * @param projectorClassroom The classroom where the projector is located.
	 * @param commandActionName  The action to be performed on the projector.
	 * @return A ServerEvent entity populated with the correct details.
	 * @throws ProjectorServerException If any of the entities cannot be found or if
	 *                                  input parameters are invalid.
	 */
	private ServerEvent createServerEventEntity(String projectorModelName, String projectorClassroom,
			String commandActionName) throws ProjectorServerException
	{
		// Check if any of the parameters is null or empty/blank string.
		if (projectorModelName == null || projectorModelName.isBlank() || projectorClassroom == null
				|| projectorClassroom.isBlank() || commandActionName == null || commandActionName.isBlank())
		{
			// if blank or null throw exception.
			throw new ProjectorServerException(505, "Null parameter received while during server event creation.");
		}

		// Log the action for traceability.
		log.info("Creating '{}' event for projector '{}' in classroom '{}'.", commandActionName, projectorModelName,
				projectorClassroom);

		/* -------------- FORMING PROJECTOR ENTITY -------------- */
		// Retrieve the projector model entity from the database.
		Optional<ProjectorModel> projectorModelOpt = this.projectorModelRepository.findById(projectorModelName);

		// If the model exists, store in entity, otherwise throw an exception.
		ProjectorModel projectorModelEntity = projectorModelOpt.orElseThrow(() ->
		{
			String message = "The projector model '" + projectorModelName + "' does not exist.";
			log.error(message);
			return new ProjectorServerException(494, message);
		});

		log.debug("PROJECTOR MODEL RETRIEVED: {}", projectorModelEntity);

		// Retrieve the classroom entity from the database.
		Optional<Classroom> classroomOpt = this.classroomRepository.findById(projectorClassroom);

		// If the classroom exists, store in entity, otherwise throw an exception.
		Classroom classroomEntity = classroomOpt.orElseThrow(() ->
		{
			String message = "The classroom '" + projectorClassroom + "' does not exist.";
			log.error(message);
			return new ProjectorServerException(494, message);
		});

		log.debug("CLASSROOM RETRIEVED: {}", classroomEntity);

		// Create composite projector ID to query the projector entity.
		ProjectorId projectorId = new ProjectorId();
		projectorId.setClassroom(classroomEntity);
		projectorId.setModel(projectorModelEntity);

		// Retrieve the projector entity using the composite key.
		Optional<Projector> projectorOpt = this.projectorRepository.findById(projectorId);
		Projector projectorEntity = projectorOpt.orElseThrow(() ->
		{
			String message = "The projector model '" + projectorModelName + "' in classroom '" + projectorClassroom
					+ "' does not exist.";
			log.error(message);
			return new ProjectorServerException(494, message);
		});

		log.debug("PROJECTOR UNIT RETRIEVED: {}", projectorEntity);

		/* -------------------- END FORMING PROJECTOR ENTITY -------------------- */

		/* -------------------- RETRIEVE COMMAND ENTITY -------------------- */

		// Retrieve the action entity from the repository.
		Optional<Action> actionOpt = this.actionRepositories.findById(commandActionName);

		Action actionEntity = actionOpt.orElseThrow(() ->
		{
			String message = "The given action '" + commandActionName + "' does not exist.";
			log.error(message);
			return new ProjectorServerException(494, message);
		});

		log.debug("COMMAND ACTION RETRIEVED: {}", actionEntity);

		// Retrieve the command entity for the given projector model and action.
		Optional<Command> commandOpt = this.commandRepository.findByModelNameAndAction(projectorModelEntity,
				actionEntity);

		Command commandEntity = commandOpt.orElseThrow(() ->
		{
			String message = "No command found in DB for model '" + projectorModelName + "' to perform action '"
					+ commandActionName + "'.";
			log.error(message);
			return new ProjectorServerException(494, message);
		});

		log.debug("COMMAND RETRIEVED: {}", commandEntity);

		/* -------------------- END RETRIEVE COMMAND ENTITY -------------------- */

		/* -------------- SET OTHER PARAMETERS FOR THE EVENT -------------- */

		// Get the current date and time for the event timestamp.
		LocalDateTime dateTime = LocalDateTime.now();

		// Placeholder for user identification (can be updated as per requirement).
		String user = "TO DO"; // TODO: Replace with actual user identification logic

		// Set the default status of the event.
		String defaultStatus = Constants.EVENT_STATUS_PENDING;

		// Create and populate the server event entity.
		ServerEvent serverEventEntity = new ServerEvent();
		serverEventEntity.setCommand(commandEntity);
		serverEventEntity.setProjector(projectorEntity);
		serverEventEntity.setActionStatus(defaultStatus);
		serverEventEntity.setDateTime(dateTime);
		serverEventEntity.setUser(user);

		// Log the event creation process.
		log.info("Server event successfully created for projector '{}', action '{}', in classroom '{}'.",
				projectorModelName, commandActionName, projectorClassroom);

		// Return the populated server event entity.
		return serverEventEntity;
	}

	// --------------------------- END UTILITY METHODS -----------------------------

	// ----------------------------- PARSING METHODS -------------------------------

	/**
	 * Endpoint to parse classrooms from a CSV file.
	 * <p>
	 * This method receives a CSV file containing classroom data, validates it, and
	 * parses the content. It returns a response indicating success or failure.
	 * </p>
	 *
	 * @param classroomsFile The uploaded CSV file containing classroom data.
	 * @return A {@link ResponseEntity} containing a success or error message.
	 */
	@Transactional
	@PostMapping(value = "/parse-classrooms")
	public ResponseEntity<?> parseClassrooms(@RequestParam(value = "classrooms.csv") MultipartFile classroomsFile)
	{

		// Log the incoming request with the file name
		log.info("Received request to parse classrooms.");

		String message;

		// Use "try-with-resources" to automatically close the scanner when done.
		try (Scanner scanner = new Scanner(classroomsFile.getInputStream()))
		{

			this.validateFile(classroomsFile);

			// Log the successful file validation
			log.info("Projectors file successfully validated.");

			// Parse the classrooms from the CSV file (parser returns a string with a
			// result.)
			message = classroomParser.parseClassroom(scanner);

			// Wrap the result in a ResponseDto
			ResponseDto response = new ResponseDto(Constants.RESPONSE_STATUS_SUCCESS, message);

			// Return a success response
			log.debug("Returning success response with status: {}", HttpStatus.CREATED);
			return ResponseEntity.status(HttpStatus.CREATED).body(response);

		} catch (IOException e)
		{
			// Log file reading errors
			log.error("IO error while reading classrooms file: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError().body("Error encountered while reading the file.");

		} catch (ProjectorServerException e)
		{
			// Log custom projector server exceptions
			log.error("Projector server error while processing classrooms file: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError().body(e.getMapError());

		} catch (Exception e)
		{
			// Log unexpected errors
			log.error("Unexpected error while processing classrooms file: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError().body("ERROR: Unexpected exception occurred.");
		}
	}

	/**
	 * Handles the upload and parsing of a CSV file containing projector model data.
	 * <p>
	 * This endpoint receives a CSV file (expected to be named "models.csv"), reads
	 * its content, and processes the data using the {@code projectorModelsParser}.
	 * The file is validated to ensure it is not empty and is in CSV format. If an
	 * error occurs during parsing, an appropriate error message is returned to the
	 * client.
	 * </p>
	 *
	 * @param file The CSV file containing projector model data, expected as
	 *             "models.csv".
	 * @return A {@link ResponseEntity} containing the result of the parsing
	 *         operation or an error message.
	 */
	@Transactional
	@PostMapping("/parse-models")
	public ResponseEntity<?> parseModels(@RequestParam("models.csv") MultipartFile file)
	{

		// Log the incoming request for processing models with a unique request ID for
		// traceability.
		log.info("Received request to parse projector models.");

		String message;

		// Use "try-with-resources" to automatically close the scanner when done with
		// the InputStream
		try (Scanner scanner = new Scanner(file.getInputStream()))
		{

			// Validate the file (empty check, CSV format check)
			this.validateFile(file);

			// Log validation success
			log.info("Models file successfully validated.");

			// Parse the models from the CSV file using the projectorModelsParser
			message = projectorModelsParser.parseProjectorModels(scanner);

			// Create and return a success response with the parsed message
			ResponseDto response = new ResponseDto(Constants.RESPONSE_STATUS_SUCCESS, message);
			return ResponseEntity.status(HttpStatus.CREATED).body(response);

		} catch (IOException e)
		{
			// Log and return an error response in case of an IO exception (e.g., reading
			// the file)
			log.error("Error reading models file: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError().body("Error encountered while reading the file.");
		} catch (ProjectorServerException e)
		{
			// Log and return an error response for custom exceptions (e.g., parsing issues)
			log.error("Projector server error processing models file: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError().body(e.getMapError());
		} catch (Exception e)
		{
			// Log and return an error response for unexpected exceptions
			log.error("Unexpected error occurred while processing models file: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError().body("ERROR: Unexpected exception occurred.");
		}
	}

	/**
	 * Handles the upload and parsing of a CSV file containing projector data.
	 * <p>
	 * This endpoint receives a CSV file (expected to be named "projectors.csv"),
	 * reads its contents, and processes the data using the {@code projectorParser}.
	 * In case of an error during the processing or parsing, an appropriate error
	 * message is returned to the client.
	 * </p>
	 *
	 * @param file The CSV file containing projector data, expected as
	 *             "projectors.csv".
	 * @return A {@link ResponseEntity} containing the parsing result or an error
	 *         message.
	 */
	@Transactional
	@PostMapping("/parse-projectors")
	public ResponseEntity<?> parseProjectors(@RequestParam("projectors.csv") MultipartFile file)
	{

		// Log the incoming request to parse the projectors data file.
		log.info("Received request to parse projectors.");

		String message;
		try (Scanner scanner = new Scanner(file.getInputStream()))
		{

			// Validate the file before processing (check if it is non-empty and is in CSV
			// format).
			this.validateFile(file);

			// Log the successful file validation
			log.info("Projectors file successfully validated.");

			// Parse the CSV file using the projectorParser and obtain a result message
			message = projectorParser.parseProjectors(scanner);

			// Create and return a success response with the parsing message
			ResponseDto response = new ResponseDto(Constants.RESPONSE_STATUS_SUCCESS, message);
			return ResponseEntity.status(HttpStatus.CREATED).body(response);

		} catch (IOException e)
		{
			// Log and return an error response in case of an IO exception (e.g., issue
			// reading the file)
			log.error("Error reading file '{}': {}", file.getOriginalFilename(), e.getMessage(), e);
			return ResponseEntity.internalServerError().body("Error encountered while reading the file.");
		} catch (ProjectorServerException e)
		{
			// Log and return an error response for custom exceptions (e.g., parsing or
			// business logic errors)
			log.error("Projector server error processing file '{}': {}", file.getOriginalFilename(), e.getMessage(), e);
			return ResponseEntity.internalServerError().body(e.getMapError());
		} catch (Exception e)
		{
			// Log and return an error response for unexpected exceptions (fallback)
			log.error("Unexpected error occurred while processing file '{}': {}", file.getOriginalFilename(),
					e.getMessage(), e);
			return ResponseEntity.internalServerError().body("ERROR: Unexpected exception occurred.");
		}
	}

	/**
	 * Endpoint to parse commands from a CSV file.
	 * <p>
	 * This method receives a CSV file containing command data, validates the file,
	 * and parses the content. It returns a response indicating success or failure.
	 * </p>
	 *
	 * @param file The uploaded CSV file containing command data.
	 * @return A {@link ResponseEntity} containing a success or error message.
	 */
	@Transactional
	@PostMapping("/parse-commands")
	public ResponseEntity<?> parseCommands(@RequestParam("commands.csv") MultipartFile file)
	{

		// Log the incoming request, including the file name for traceability
		log.info("Received request to parse commands.");

		// Message that will be returned to the client after processing
		String message;

		// Use "try-with-resources" to automatically close the scanner after use
		try (Scanner scanner = new Scanner(file.getInputStream()))
		{

			// Validate the file (checks for format, empty file, etc.)
			this.validateFile(file);

			// Log the successful file validation
			log.info("Commands file successfully validated.");

			// Parse the commands from the CSV file using commandsParser
			message = commandsParser.parseCommands(scanner);

			// Create and return a success response with the parsing message
			ResponseDto response = new ResponseDto(Constants.RESPONSE_STATUS_SUCCESS, message);
			return ResponseEntity.status(HttpStatus.CREATED).body(response);

		} catch (IOException e)
		{
			// Log the IO error when reading the file
			log.error("IO error while reading file '{}': {}", file.getOriginalFilename(), e.getMessage(), e);
			return ResponseEntity.internalServerError().body("Error encountered while reading the file.");

		} catch (ProjectorServerException e)
		{
			// Log and return custom errors related to the projector server (parsing or
			// business logic errors)
			log.error("Projector server error while processing commands file '{}': {}", file.getOriginalFilename(),
					e.getMessage(), e);
			return ResponseEntity.internalServerError().body(e.getMapError());

		} catch (Exception e)
		{
			// Log unexpected errors to capture any other issues
			log.error("Unexpected error while processing commands file '{}': {}", file.getOriginalFilename(),
					e.getMessage(), e);
			return ResponseEntity.internalServerError().body("ERROR: Unexpected exception occurred.");
		}
	}

	/**
	 * Handles the parsing of multiple CSV files: projectors, commands, and
	 * classrooms. This endpoint accepts optional files and processes them
	 * accordingly. If no files are provided, it returns an error response.
	 * <p>
	 * The method performs the following steps:
	 * <ul>
	 * <li>Validates the received files.</li>
	 * <li>Parses the projectors file if provided.</li>
	 * <li>Parses the commands file if provided.</li>
	 * <li>Parses the classrooms file if provided.</li>
	 * <li>Returns a structured response with parsing results.</li>
	 * </ul>
	 * </p>
	 *
	 * @param classroomsFile Multipart file containing classroom data (Optional).
	 * @param projectorsFile Multipart file containing projector data (Optional).
	 * @param commandsFile   Multipart file containing command data (Optional).
	 * @return ResponseEntity containing a success message or error response.
	 * @throws ProjectorServerException If an issue occurs related to the server
	 *                                  logic.
	 * @throws IOException              If there is an error reading the provided
	 *                                  files.
	 * @throws Exception                If an unexpected error occurs.
	 */
	@Transactional
	@PostMapping("/parse-multifile")
	public ResponseEntity<?> parseMultifile(
			@RequestParam(value = "classrooms.csv", required = false) MultipartFile classroomsFile,
			@RequestParam(value = "projectors.csv", required = false) MultipartFile projectorsFile,
			@RequestParam(value = "commands.csv", required = false) MultipartFile commandsFile)
	{

		try
		{
			log.info("Call to parse multiple files received.");

			// Initialize message and response DTO for structured results.
			String message = "";
			RichResponseDto richResponseDto = new RichResponseDto();

			// Check if no files were received, and return an error message.
			if ((classroomsFile == null || classroomsFile.isEmpty()) && (commandsFile == null || commandsFile.isEmpty())
					&& (projectorsFile == null || projectorsFile.isEmpty()))
			{
				message = "No files received for parse operation.";
				log.error(message);
				throw new ProjectorServerException(498, message); // Custom error for no files received.
			}

			// NOTE: IF YOU ARE MODIFYING THIS KEEP IN MIND THE PARSING ORDER MATTERS TO
			// KEEP A CONSISTENT RESULT MESSAGE.
			// BEST ORDER IS: CLASSROOMS > PROJECTORS > COMMANDS.

			// Process classrooms file if provided.
			if (classroomsFile != null && !classroomsFile.isEmpty())
			{
				log.info("Processing 'classrooms.csv' file.");
				try (Scanner scanner = new Scanner(classroomsFile.getInputStream()))
				{
					this.validateFile(classroomsFile); // Validate the file before processing.
					richResponseDto.setMessage3(this.classroomParser.parseClassroom(scanner)); // Parse classrooms.
				}
			} else
			{
				log.info("No 'classrooms.csv' file received.");
				richResponseDto.setMessage3("The request did not include a file for classrooms.");
			}

			// Process projectors file if provided.
			if (projectorsFile != null && !projectorsFile.isEmpty())
			{
				log.info("Processing 'projectors.csv' file.");
				try (Scanner scanner = new Scanner(projectorsFile.getInputStream()))
				{
					this.validateFile(projectorsFile); // Validate the file before processing.
					richResponseDto.setMessage2(projectorParser.parseProjectors(scanner)); // Parse projectors.
				}
			} else
			{
				log.info("No 'projectors.csv' file received.");
				richResponseDto.setMessage2("The request did not include a file for projectors.");
			}

			// Process commands file if provided.
			if (commandsFile != null && !commandsFile.isEmpty())
			{
				log.info("Processing 'commands.csv' file.");
				try (Scanner scanner = new Scanner(commandsFile.getInputStream()))
				{
					this.validateFile(commandsFile); // Validate the file before processing.
					richResponseDto.setMessage1(this.commandsParser.parseCommands(scanner)); // Parse commands.
				}
			} else
			{
				log.info("No 'commands.csv' file received.");
				richResponseDto.setMessage1("The request did not include a file for commands.");
			}

			// Set response status to success.
			richResponseDto.setStatus(Constants.RESPONSE_STATUS_SUCCESS);

			// Log the final parsing results.
			log.info("Commands: {}\nProjectors: {}\nClassrooms: {}", richResponseDto.getMessage1(),
					richResponseDto.getMessage2(), richResponseDto.getMessage3());

			// Return the structured response DTO with success status.
			return ResponseEntity.ok(richResponseDto);

		} catch (ProjectorServerException e)
		{
			// Handle custom exceptions (e.g., invalid input, no files received).
			log.error("Projector server error: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError().body(e.getMapError());

		} catch (IOException e)
		{
			// Handle IO errors when reading the files.
			log.error("Error reading the file: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError().body("Error encountered while reading the file.");

		} catch (Exception e)
		{
			// Catch any unexpected errors.
			log.error("Unexpected error: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError().body("ERROR: Unexpected exception occurred.");
		}
	}

	// --------------------------- END PARSING METHODS -----------------------------

	// -------------------------- SERVER EVENT METHODS -----------------------------

	/**
	 * Endpoint to create a batch of server events for multiple projectors.
	 * 
	 * This method receives a batch of projector events, iterates over them, and
	 * creates corresponding server events. The events are then persisted in the
	 * database in a single transaction.
	 * 
	 * @param serverEventBatchDto DTO containing the details of the server events to
	 *                            be created.
	 * @return A ResponseEntity containing the status and message of the operation.
	 */
	@Transactional
	@PostMapping(value = "/server-events-batch")
	public ResponseEntity<?> createServerEventBatch(
			@RequestBody(required = true) ServerEventBatchDto serverEventBatchDto)
	{
		try
		{
			log.debug("Request for batch command received");

			// Prepare the response object with success status and message
			ResponseDto response = new ResponseDto();

			// Extract action name from the request DTO
			String commandActionName = serverEventBatchDto.getAction();

			// Extract list of projectors from the request DTO
			List<ProjectorDto> projectorList = serverEventBatchDto.getProjectorList();

			if (projectorList.isEmpty())
			{
				response.setMessage("NULL PROJCETOR LIST.");
				response.setStatus("ERROR");
				return ResponseEntity.status(HttpStatus.CREATED).body(response);
			}

			// Initialize list to hold the server events to be saved
			List<ServerEvent> serverEventList = new ArrayList<>();

			// Loop through each projector and create a corresponding server event
			for (ProjectorDto projectorDto : projectorList)
			{
				// Create server event for each projector and add it to the list
				serverEventList.add(this.createServerEventEntity(projectorDto.getModel(), projectorDto.getClassroom(),
						commandActionName));
			}

			// Log the number of events being saved for traceability
			log.info("Creating and saving {} server events to the database.", serverEventList.size());

			// Persist all server events in a single transaction for efficiency
			this.serverEventRepository.saveAllAndFlush(serverEventList);

			// Log successful operation
			log.info("{} server events successfully created and saved to the database.", serverEventList.size());

			response.setMessage(serverEventList.size() + " events successfully created.");
			response.setStatus(Constants.RESPONSE_STATUS_SUCCESS);

			// Return a success response with the created events count
			return ResponseEntity.status(HttpStatus.CREATED).body(response);

		} catch (ProjectorServerException ex)
		{
			// Log the exception error for debugging purposes
			log.error("Error occurred while processing server event batch: {}", ex.getMessage(), ex);

			// Return a bad request response with error details
			return ResponseEntity.badRequest().body(ex.getMapError());
		}
	}

	@Transactional
	@PutMapping(value = "/server-events")
	public ResponseEntity<?> updateServerEventStatus(@RequestParam(name = "eventId") String eventId,
			@RequestParam(name = "eventStatus") String eventStatus,
			@RequestParam(name = "newStatus") String eventNewStatus)
	{
		try
		{

			log.info("eventId" + eventId);
			log.info("eventStatus" + eventStatus);
			log.info("eventNewStatus" + eventNewStatus);

			// Prepare the response object with success status and message
			ResponseDto response = new ResponseDto();
			String message;

			if (eventId == null || eventId.isBlank() || eventStatus == null || eventStatus.isBlank()
					|| eventNewStatus == null || eventNewStatus.isBlank())
			{
				message = "No se han seleccionado los parametros adecuados.";
				log.error(message);
				response.setStatus(Constants.RESPONSE_STATUS_ERROR);
				response.setMessage(message);
				return ResponseEntity.badRequest().body(response);
			}

			Long eventIdLong = Long.valueOf(eventId);

			Optional<ServerEvent> serverEventOpt = this.serverEventRepository.findById(eventIdLong);

			ServerEvent serverEventEntity = serverEventOpt.orElseThrow(() ->
			{
				String messagex = "The server event with ID'" + eventIdLong + "' does not exist.";
				log.error(messagex);
				return new ProjectorServerException(494, messagex);
			});

			// For each entry in the possible events constant a comparison is made and if no
			// coincidence
			// is found an error is returned because the new event status is not
			// accepptable.
			if (!(Arrays.stream(Constants.POSSIBLE_EVENTS).anyMatch(event -> event.equals(eventNewStatus))))
			{
				message = "Event " + eventNewStatus + " doesnt exist.";
				log.error(message);
				throw new ProjectorServerException(499, message);
			}

			serverEventEntity.setActionStatus(eventNewStatus);

			message = "Event with ID" + eventId + " sucessfully updated to: " + eventNewStatus;

			log.info(message);

			response.setStatus(Constants.RESPONSE_STATUS_SUCCESS);
			response.setMessage(message);

			this.serverEventRepository.saveAndFlush(serverEventEntity);

			return ResponseEntity.ok().body(response);

		} catch (ProjectorServerException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(e.getMapError());
		}
	}

	// ------------------------ END SERVER EVENT METHODS ---------------------------

	// ----------------------- FLOORS & CLASSROOMS METHODS -------------------------

	/**
	 * Retrieves a list of all floors recorded in the database.
	 * 
	 * This endpoint fetches all available floor details from the repository and
	 * returns them as a list of FloorDto objects.
	 * 
	 * Note: This method is only for front-end QOL improvements.
	 * 
	 * @return A ResponseEntity containing the list of floors, or a no-content
	 *         response if no floors are found.
	 */
	@GetMapping(value = "/floors")
	public ResponseEntity<?> getFloorList()
	{
		log.info("Received request to fetch floor list.");

		try
		{
			// Retrieve all floor data from the repository
			List<FloorDto> floors = this.floorRepository.findAllDto();

			// Log the number of retrieved floors (avoiding large data dump in the logs).
			log.info("Retrieved {} floor(s).", floors.size());

			// Return the list of floors, or no content if the list is empty.
			if (floors.isEmpty())
			{
				log.warn("No floors found in the database.");
				return ResponseEntity.noContent().build(); // Returning HTTP 204 No Content.
			}

			return ResponseEntity.ok().body(floors);

		} catch (Exception ex)
		{
			// Log any error or exception that might occur.
			log.error("Error occurred while retrieving floor list: {}", ex.getMessage(), ex);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("An error occurred while fetching floor data.");
		}
	}

	/**
	 * Retrieves a list of classrooms based on the parameter floor name.
	 * 
	 * This endpoint fetches all classrooms located on the specified floor and
	 * returns them as a list of ClassroomDto objects.
	 * 
	 * @param floor The name of the floor to filter the classrooms by.
	 * @return A ResponseEntity containing the list of classrooms or a no-content
	 *         response if no classrooms are found.
	 */
	@GetMapping(value = "/classrooms")
	public ResponseEntity<?> getClassroomList(@RequestParam(required = true) String floor)
	{
		log.info("Received request to fetch classrooms for floor '{}'.", floor);

		try
		{
			// Fetch the list of classrooms for the given floor
			List<ClassroomDto> classrooms = this.classroomRepository.findDtoListByFloorName(floor);

			// Log the number of classrooms retrieved, avoid logging large data dumps
			log.info("Retrieved {} classroom(s) for floor '{}'.", classrooms.size(), floor);

			// If no classrooms were found, return a 204 No Content status
			if (classrooms.isEmpty())
			{
				log.warn("No classrooms found for floor '{}'.", floor);
				return ResponseEntity.noContent().build(); // HTTP 204 No Content
			}

			return ResponseEntity.ok().body(classrooms);

		} catch (Exception ex)
		{
			// Log any errors that occur during the process
			log.error("Error occurred while retrieving classrooms for floor '{}': {}", floor, ex.getMessage(), ex);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("An error occurred while fetching classroom data.");
		}
	}

	// --------------------- END FLOORS & CLASSROOMS METHODS -----------------------

	// ---------------------------- PROJECTOR METHODS ------------------------------

	/**
	 * Assigns a projector model to a classroom.
	 * <p>
	 * This method processes the assignment of a projector to a classroom. It first
	 * validates that both the projector model and the classroom exist in the
	 * database. If both entities are found, a new projector entity is created and
	 * assigned to the specified classroom. If any errors occur during this process
	 * (e.g., missing entities), appropriate exceptions are thrown.
	 * </p>
	 * 
	 * @param projectorDto The Data Transfer Object (DTO) containing the projector
	 *                     model name and the classroom name to which the projector
	 *                     will be assigned.
	 * @return ResponseEntity<?> The response entity containing the status of the
	 *         operation. If the assignment is successful, it returns HTTP status
	 *         201 (Created) with a success message. If errors are encountered, it
	 *         returns an HTTP status 500 (Internal Server Error) with the
	 *         appropriate error message.
	 * 
	 * @throws ProjectorServerException if the classroom or projector model does not
	 *                                  exist, or any other failure occurs during
	 *                                  the assignment process.
	 */
	@Transactional
	@PostMapping("/projectors")
	public ResponseEntity<?> createNewProjector(@RequestBody ProjectorDto projectorDto)
	{
		try
		{
			// Log the receipt of the assignment request
			log.info("Received request to assign projector to classroom.");

			String modelName = projectorDto.getModel();
			String classroomName = projectorDto.getClassroom();

			// Log the projector and classroom details for better traceability
			log.debug("Assigning projector model '{}' to classroom '{}'.", modelName, classroomName);

			// Check if the classroom exists in the database
			Classroom classroomEntity = classroomRepository.findById(classroomName).orElseThrow(
					() -> new ProjectorServerException(HttpStatus.NOT_FOUND.value(), "Classroom does not exist."));

			log.debug("Classroom '{}' successfully retrieved.", classroomName);

			// Check if the projector model exists in the database
			ProjectorModel projectorModelEntity = projectorModelRepository.findById(modelName)
					.orElseThrow(() -> new ProjectorServerException(HttpStatus.NOT_FOUND.value(),
							"Projector model does not exist."));

			log.debug("Projector model '{}' successfully retrieved.", modelName);

			// At this point, both the classroom and projector model exist, so we proceed to
			// assignment

			// Create a new projector entity and associate it with the classroom and
			// projector model
			Projector projectorEntity = new Projector();
			projectorEntity.setClassroom(classroomEntity);
			projectorEntity.setModel(projectorModelEntity);

			// Save the newly created projector entity
			projectorRepository.save(projectorEntity);

			// Log success message and return a response
			String successMessage = String.format("Projector '%s' successfully assigned to classroom '%s'.", modelName,
					classroomName);
			log.debug(successMessage);

			// Prepare the response DTO with the success status and message
			ResponseDto responseDto = new ResponseDto(Constants.RESPONSE_STATUS_SUCCESS, successMessage);

			// Return HTTP status 201 (Created) as a projector has been successfully
			// assigned
			return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
		} catch (ProjectorServerException e)
		{
			// Log the error and return a response with a custom error message and status
			log.error("Error assigning projector: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMapError());
		} catch (Exception e)
		{
			// Log unexpected errors and return a generic error message with status 500
			log.error("Unexpected error while assigning projector: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
		}
	}

	/**
	 * Retrieves a paginated list of projectors based on the specified criteria.
	 * <p>
	 * This method retrieves a list of projectors, either ordered by model name or
	 * by floor and classroom, based on the provided criteria. If the criteria is
	 * not specified, the default order is by floor and classroom. Pagination is
	 * applied using the Pageable parameter.
	 * </p>
	 * 
	 * @param criteria Optional query parameter to specify the ordering criteria. If
	 *                 provided and set to "modelname", the projectors will be
	 *                 ordered by model name. Otherwise, the projectors will be
	 *                 ordered by floor and classroom.
	 * @param pageable Pageable object for pagination (page number and page size).
	 * @return ResponseEntity<?> The response entity containing the paginated list
	 *         of projectors and the appropriate status code.
	 * 
	 * @throws Exception if there is any issue during the retrieval process.
	 */
	@GetMapping("/projectors")
	public ResponseEntity<?> getProjectorList(@RequestParam(value = "criteria", required = false) String criteria,
			@RequestParam(value = "classroom", required = false) String classroom,
			@RequestParam(value = "floor", required = false) String floor,
			@RequestParam(value = "model", required = false) String model,
			@PageableDefault(page = 0, size = 15) Pageable pageable)
	{
		// Log the received request and the provided criteria
		log.info("Received request to fetch projectors ordered by: {}", criteria);

		Page<ProjectorInfoDto> projectors;
		String message;

		// Default criteria to compare for 'modelname'
		String modelNameCriteria = "modelname";

		// -----------------------------------------------------
		// | TODO: Change current logic with ENUM-based logic. |
		// -----------------------------------------------------

		// Determine the order of projectors based on the criteria
		if (criteria != null && !criteria.isBlank() && modelNameCriteria.equals(criteria.toLowerCase().trim()))
		{
			// If criteria is 'modelname', order projectors by model name
			log.debug("Ordering projectors by model name.");
			projectors = projectorRepository.getProjectorOrderByModelName(pageable, classroom, floor, model);
			message = "Projectors list ordered by model name.";
		} else
		{
			// Default: order projectors by floor and classroom
			log.debug("Ordering projectors by floor and classroom.");
			projectors = projectorRepository.getProjectorsOrderByFloorAndClassroom(pageable, classroom, floor, model);
			message = "Projectors list ordered by floor and classroom.";
		}

		// Log the final message based on the ordering criteria
		log.info(message);

		// Return the paginated list of projectors with a 200 OK status
		return ResponseEntity.status(HttpStatus.OK).body(projectors);
	}

	/**
	 * Removes a projector from a classroom.
	 * <p>
	 * This method handles the removal of a projector from a classroom by performing
	 * the following checks: 1. Verifies that the provided classroom exists. 2.
	 * Verifies that the provided projector model exists. 3. Ensures that the
	 * projector assignment to the classroom exists before proceeding with removal.
	 * 4. If any error occurs during the process (e.g., classroom or projector model
	 * does not exist), appropriate exceptions are thrown.
	 * </p>
	 *
	 * @param projectorDto The Data Transfer Object (DTO) containing the classroom
	 *                     and projector model information to be removed.
	 *
	 * @return ResponseEntity The response entity containing the status of the
	 *         operation: - If the removal is successful, returns HTTP status 200
	 *         (OK) with a success message. - If any errors are encountered, returns
	 *         HTTP status 500 (Internal Server Error) with an error message.
	 *
	 * @throws ProjectorServerException If: - The classroom does not exist. - The
	 *                                  projector model does not exist. - The
	 *                                  projector assignment does not exist.
	 */
	@Transactional
	@DeleteMapping("/projectors")
	public ResponseEntity<?> deleteProjector(@RequestBody List<ProjectorInfoDto> projectorDtoList)
	{
		try
		{
			log.info("Received request to delete projectors batch");

			// Response DTO for returning status
			ResponseDto responseDto = new ResponseDto();
			String message;
			List<Projector> projectorEntitiList = new ArrayList();

			for (ProjectorInfoDto projectorDto : projectorDtoList)
			{
				String modelName = projectorDto.getModel();
				String classroomName = projectorDto.getClassroom();

				// Find the classroom in the database
				Classroom classroomEntity = classroomRepository.findById(classroomName)
						.orElseThrow(() -> new ProjectorServerException(499, "Classroom does not exist."));

				// Log the retrieval of the classroom entity
				log.debug("Classroom '{}' found in the database.", classroomName);

				// Find the projector model in the database
				ProjectorModel projectorModelEntity = projectorModelRepository.findById(modelName)
						.orElseThrow(() -> new ProjectorServerException(499, "Projector model does not exist."));

				// Log the retrieval of the projector model
				log.debug("Projector model '{}' found in the database.", modelName);

				// Create the composite ID for the projector
				ProjectorId projectorId = new ProjectorId();
				projectorId.setClassroom(classroomEntity);
				projectorId.setModel(projectorModelEntity);

				// Check if the projector assignment exists.
				Optional<Projector> projectorOpt = projectorRepository.findById(projectorId);
				Projector projectorEntity = projectorOpt
						.orElseThrow(() -> new ProjectorServerException(499, "Projector assignment does not exist."));

				// Log the projector assignment found.
				log.debug("Projector assignment found for model '{}' in classroom '{}'.", modelName, classroomName);

				// Add the current projector for later deletion order.
				projectorEntitiList.add(projectorEntity);

				// Log successful removal.
				message = "Projector " + modelName + " from classroom " + classroomName + "added to remove list.";
				log.info(message);
			}

			this.projectorRepository.deleteAll(projectorEntitiList);

			// Set the response DTO.
			responseDto.setStatus(Constants.RESPONSE_STATUS_SUCCESS);
			responseDto.setMessage(String.format("Successfully removed %d projectors.", projectorEntitiList.size()));

			// Return the success response.
			return ResponseEntity.status(HttpStatus.OK).body(responseDto);

		} catch (DataIntegrityViolationException e)
		{
			log.error("Projector removal failed due to data integrity issues: {}", e.getMessage());
			return ResponseEntity.internalServerError().body(e.getMessage());
		}

		catch (ProjectorServerException e)
		{
			// Log the error for known exceptions (e.g., classroom or model not found).
			log.error("Projector removal failed: {}", e.getMessage());
			return ResponseEntity.internalServerError().body(e.getMapError());
		} catch (Exception e)
		{
			// Log the error for unexpected exceptions
			log.error("Unexpected error encountered while removing projector: {}", e.getMessage());
			return ResponseEntity.internalServerError().body("Unexpected error occurred: " + e.getMessage());
		}
	}

	// Recibe una lista de proyectores DTO, por cada uno de ellos controla si
	// existe, si existe lo añade a la lsita y lueygo la lista una vez compelta la
	// borra.

	/**
	 * Retrieves a list of projectors assigned to a specific classroom.
	 * <p>
	 * This method handles the retrieval of projectors assigned to the given
	 * classroom. It first checks if the classroom exists in the system. If the
	 * classroom exists, it queries the database to fetch the list of projectors
	 * associated with the classroom. If the classroom does not exist, it returns a
	 * 404 Not Found response with a relevant message.
	 * </p>
	 *
	 * @param classroom The name of the classroom for which projectors need to be
	 *                  fetched. This parameter is required and should not be blank.
	 * 
	 * @return ResponseEntity A response entity containing the list of projectors
	 *         (HTTP 200 OK) or an error message if the classroom does not exist
	 *         (HTTP 404 Not Found).
	 */
	@GetMapping(value = "/classroom-projectors")
	public ResponseEntity<?> getProjectorsByClassroom(@RequestParam(required = true) String classroom)
	{
		try
		{
			// Log the incoming request
			log.info("Received request to get projectors for classroom: {}", classroom);

			// Check if the classroom exists
			boolean classroomExists = this.classroomRepository.existsById(classroom);

			if (!classroomExists)
			{
				String message = "Classroom " + classroom + " does not exist.";
				log.warn(message); // Log as warning when the classroom doesn't exist
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message);
			}

			// Retrieve the list of projectors for the provided classroom
			List<ProjectorDto> projectors = this.projectorRepository.getProjectorByClassroom(classroom);

			// If no projectors found, return a suitable message
			if (projectors.isEmpty())
			{
				String message = "No projectors assigned to classroom: " + classroom;
				log.warn(message); // Log as warning if no projectors are assigned to the classroom
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message);
			}

			// Log the successful retrieval of projectors
			log.info("Successfully retrieved {} projectors for classroom: {}", projectors.size(), classroom);

			// Return the projectors list with HTTP status 200 (OK)
			return ResponseEntity.ok().body(projectors);

		} catch (Exception e)
		{
			// Log any unexpected errors and return a 500 (Internal Server Error) response
			String message = "Error encountered while retrieving projectors for classroom: " + classroom;
			log.error(message, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message + ": " + e.getMessage());
		}
	}

	// -------------------------- END PROJECTOR METHODS ----------------------------

	// ------------------------------ MODEL METHODS --------------------------------

	/**
	 * Creates a new projector model in the system.
	 * <p>
	 * This method handles the creation of a new projector model. It first checks if
	 * the model name is provided, and then verifies whether the model already
	 * exists in the database. If the model does not exist, it is created and saved.
	 * If the model already exists or if the model name is blank, appropriate
	 * exceptions are thrown.
	 * </p>
	 * 
	 * @param projectorDto The Data Transfer Object (DTO) containing the projector
	 *                     model details, including the model name.
	 * @return ResponseEntity A response entity containing the status of the
	 *         operation. If the projector model is created successfully, a message
	 *         confirming the upload is returned with HTTP status 201 (Created). In
	 *         case of errors, a custom error message is returned with the
	 *         corresponding error code and HTTP status.
	 * @throws ProjectorServerException if the projector model already exists in the
	 *                                  database or if the model name is blank.
	 */
	@PostMapping("/projector-models")
	public ResponseEntity<?> createNewModel(@RequestBody() ProjectorModelDto projectorDto)
	{
		try
		{
			// Log the incoming request for processing models
			log.info("Received request to create projector model.");

			String modelName = projectorDto.getModelname();

			if (modelName == null || modelName.isBlank())
			{
				// If the model name is blank or null
				String message = "No projector model name given.";
				log.error(message);
				throw new ProjectorServerException(496, message);
			}

			log.info("Projector model name: '{}'", modelName);

			// Check if the model already exists in the database
			Optional<ProjectorModel> existingModel = projectorModelRepository.findById(modelName);
			if (existingModel.isPresent())
			{
				// If the projector already exists, throw an exception
				String message = "The projector model '" + modelName + "' already exists in the database.";
				log.error(message);
				throw new ProjectorServerException(496, message);
			}

			// If the model does not exist, create and save it
			ProjectorModel projectorModel = new ProjectorModel();
			projectorModel.setModelName(modelName);

			projectorModelRepository.saveAndFlush(projectorModel);

			// Log successful creation
			String successMessage = "Projector model '" + modelName + "' created successfully.";
			log.debug(successMessage);

			ResponseDto response = new ResponseDto(Constants.RESPONSE_STATUS_SUCCESS, successMessage);

			return ResponseEntity.status(HttpStatus.CREATED).body(response);

		} catch (ProjectorServerException e)
		{
			// Log and handle known exceptions
			log.error("Error encountered: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMapError());
		} catch (Exception e)
		{
			// Log and handle unexpected exceptions
			String message = "Unexpected error encountered during projector model upload.";
			log.error(message, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message + " " + e.getMessage());
		}
	}

	/**
	 * Retrieves a list of all projector models from the database.
	 * <p>
	 * This endpoint queries the database to fetch all the available projector
	 * models in the form of a list of {@link ProjectorModelDto}. If no models are
	 * found, it returns a 404 Not Found response with a relevant message. In case
	 * of a server error during the retrieval process, a 500 Internal Server Error
	 * response is returned with a message indicating an unexpected error.
	 * </p>
	 * 
	 * @return ResponseEntity<?> The response entity containing the status code and
	 *         either the list of projector models (HTTP 200 OK) or an error message
	 *         (HTTP 404 Not Found or HTTP 500 Internal Server Error).
	 * 
	 * @throws Exception If an error occurs while retrieving the projector models
	 *                   from the database.
	 */
	@GetMapping("/projector-models")
	public ResponseEntity<?> getModelsList()
	{
		try
		{
			// Log the start of the retrieval process
			log.info("GET call to '/projector-models' received.");

			// Retrieve the list of projector models from the database
			List<ProjectorModelDto> projectorModelList = this.projectorModelRepository.getProjectorDtoList();

			// Check if the list is empty
			if (projectorModelList.isEmpty())
			{
				// Log if no models are found
				log.warn("No projector models found in the database.");

				// Return a 404 Not Found response with a message
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
						"No projector models found in the database. Please ensure models are added before retrieving the list.");
			}

			// Log the successful retrieval of the models
			log.info("Successfully retrieved {} projector models from the database.", projectorModelList.size());

			// Return a 200 OK response with the list of projector models
			return ResponseEntity.status(HttpStatus.OK).body(projectorModelList);

		} catch (Exception e)
		{
			// Define an error message
			String message = "An unexpected error occurred while retrieving the projector model list.";

			// Log the error in case of failure with stack trace
			log.error(message, e);

			// Return a 500 Internal Server Error response with an error message
			return ResponseEntity.internalServerError().body(message);
		}
	}

	/**
	 * Deletes a projector model from the database.
	 * <p>
	 * This method handles the deletion of a projector model by performing the
	 * following checks: 1. Verifies that the model name is provided and is valid.
	 * 2. Checks if there are any projectors associated with the given model. 3. If
	 * no projectors are associated, attempts to delete the model from the database.
	 * 4. If any error occurs during the process (e.g., model not found, or
	 * projectors are still associated), appropriate exceptions are thrown.
	 * </p>
	 *
	 * @param projectorDto The Data Transfer Object (DTO) containing the projector
	 *                     model name to be deleted. The model name must be valid
	 *                     and non-blank.
	 * 
	 * @return ResponseEntity A response entity containing the status of the
	 *         operation: - If the model is successfully deleted, returns HTTP
	 *         status 200 (OK) with a success message. - If any errors are
	 *         encountered, returns an HTTP status 500 (Internal Server Error) with
	 *         an error message.
	 * 
	 * @throws ProjectorServerException if: - The projector model name is blank or
	 *                                  null. - The model does not exist in the
	 *                                  database. - There are projectors still
	 *                                  associated with the model.
	 */
	@DeleteMapping("/projector-models")
	public ResponseEntity<?> deleteModel(@RequestBody ProjectorModelDto projectorDto)
	{
		try
		{
			// Validate the input: model name must be provided
			String modelName = projectorDto.getModelname();
			if (modelName == null || modelName.isBlank())
			{
				String message = "Projector model name is required for deletion.";
				log.warn(message);
				throw new ProjectorServerException(497, message);
			}

			// Log the attempt to delete the model
			log.debug("Attempting to delete projector model: {}", modelName);

			// Check if there are any projectors associated with the given model
			long associatedProjectorCount = projectorRepository.countProjectorAssociatedModel(modelName);
			if (associatedProjectorCount > 0)
			{
				String message = String.format("Deletion failed: %d projector%s still associated with model %s.",
						associatedProjectorCount, associatedProjectorCount > 1 ? "s" : "", modelName);
				log.warn(message);
				throw new ProjectorServerException(591, message);
			}

			long associatedCommandsCount = commandRepository.countModelCommands(modelName);
			if (associatedCommandsCount > 0)
			{
				String message = String.format("Deletion failed: %d command%s still associated with model %s.",
						associatedCommandsCount, associatedCommandsCount > 1 ? "s" : "", modelName);
				log.warn(message);
				throw new ProjectorServerException(591, message);
			}

			// Attempt to find the projector model in the database
			Optional<ProjectorModel> existingModel = projectorModelRepository.findById(modelName);
			if (existingModel.isEmpty())
			{
				String message = String.format("Projector model %s not found in the database for deletion.", modelName);
				log.error(message);
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message); // Return 404 if model not found
			}

			// Proceed to delete the projector model
			projectorModelRepository.deleteById(modelName);
			String successMessage = String.format("Projector model %s successfully deleted from the database.",
					modelName);
			log.info(successMessage);

			// Return a success response
			ResponseDto response = new ResponseDto(Constants.RESPONSE_STATUS_SUCCESS, successMessage);
			return ResponseEntity.status(HttpStatus.OK).body(response);

		} catch (ProjectorServerException e)
		{
			// Handle known custom exceptions
			log.error("Projector deletion failed: {}", e.getMessage());
			return ResponseEntity.internalServerError().body(e.getMapError());
		} catch (Exception e)
		{
			// Catch any unexpected errors and return a 500 error
			String message = "Unexpected error encountered during projector model deletion.";
			log.error("{} Error: {}", message, e.getMessage());
			return ResponseEntity.internalServerError().body(message + e.getMessage());
		}
	}

	// ---------------------------- END MODEL METHODS ------------------------------

	// ------------- !!!!! TO BE REVISED FROM HERE ON !!!!! -------------
	// From this line forwards endpoints are basically bare bones and if they work
	// it is just a temporary fix. Must be revised to consolidate and secure.
	// ------------- !!!!! TO BE REVISED FROM HERE ON !!!!! -------------

	/**
	 * Endpoint que recibe una peticion por parte de un microcontrolador y devuelve
	 * una acción a realizar.
	 * 
	 * Este endpoint espera parametros utilizados para identificar el proyector con
	 * el que el micro está asociado. Esta identificacion es necesaria para poder
	 * saber que orden debe de servir el endpoint al microcontrolador para que este
	 * la re-envie al proyector.
	 * 
	 * Enviar al micro: ID accion + Orden
	 * 
	 */
	@GetMapping(value = "/server-events")
	public String serveCommandToController(@RequestParam(required = true) String projectorModel,
			@RequestParam(required = true) String projectorClassroom)
	{

		// TODO ESTO PROVISIONAL.
		Floor floor = new Floor();
		floor.setFloorName("Planta baja");

		Classroom classroom = new Classroom();
		classroom.setFloor(floor);
		classroom.setClassroomName("0.01");

		Projector projector = new Projector();
		projector.setClassroom(classroom);
		projector.setModel(this.projectorModelRepository.findById("Epson EB-S41").get());

		String actionStatus = Constants.EVENT_STATUS_PENDING;

		// recupear el ultimo comando para el modelo y aula especificado
		List<SimplifiedServerEventDto> simpleEvent = this.serverEventRepository.findMostRecentCommandOpen(projector,
				actionStatus);

		log.debug(simpleEvent.toString());

		if (simpleEvent.size() > 0)
		{
			return simpleEvent.get(0).toString();
		}

		return null;
	}

	@GetMapping(value = "/server-events-table")
	public ResponseEntity<?> serveCommandsTable()
	{

		List<TableServerEventDto> commandsList = this.serverEventRepository.getTableServerEventDtoList();

		return ResponseEntity.ok().body(commandsList);
	}

	@GetMapping(value = "/micro-greeting")
	public ResponseEntity<?> acknowledgeMicro()
	{
		log.info("Call received on /micro-greeting");

		// CommandDto cdto = new CommandDto("1","2","3");

		return ResponseEntity.ok().body("turn-on");
	}

	@GetMapping(value = "/commands")
	public ResponseEntity<?> getProjectorModelCommands(@RequestParam(required = true) String modelname)
	{
		// Controlar que el modelo exista.
		// si no existe devolver 404

		// Si no existen comandos devolver 204 (no content)

		// si existen devolver los comandos

		List<CommandDto> commands = this.commandRepository.findCommandsByModel(modelname);

		if (commands.size() < 1)
		{
			return ResponseEntity.noContent().build();
		}

		return ResponseEntity.ok().body(commands);
	}

	@GetMapping(value = "/actions")
	public ResponseEntity<?> getProjectorModelCommands()
	{

		return ResponseEntity.ok().body(this.actionRepositories.findAll());

	}

	@PostMapping(value = "/commands-page")
	public ResponseEntity<?> getCommandsPage(@PageableDefault(page = 0, size = 15) Pageable pageable,
			@RequestParam(name = "modelName", required = false) String modelName,
			@RequestParam(name = "action", required = false) String action)
	{
		log.debug("Call to fetch commands page received");

		Page<CommandDto> commandsPage = this.commandRepository.findAllCommandsPage(pageable, modelName, action);

		return ResponseEntity.ok().body(commandsPage);
	}

	@DeleteMapping(value = "/commands")
	public ResponseEntity<?> deleteCommands(@RequestBody List<CommandDto> commandsList)
	{
		try
		{
			log.debug("Call to delete commands received: {}", commandsList);

			List<Command> commandsToDelete = new ArrayList<>();
			int recordsDeleted = 0;

			for (CommandDto command: commandsList)
			{
				
				
				Action actionEntity = this.actionRepositories.findById(command.getAction()).get();
				log.debug("Action retreived");
				
				ProjectorModel modelEntity = this.projectorModelRepository.findById(command.getModelName()).get();
				log.debug("ProjectorModel retreived");
				
				CommandId commandId = new CommandId();

				commandId.setAction(actionEntity);
				commandId.setModelName(modelEntity);
				commandId.setCommand(command.getCommand());
				log.debug("CommandId retreived");
					
				Command commandEntity = this.commandRepository.findById(commandId).get();
				log.debug("Command retreived");
				
				commandsToDelete.add(commandEntity);
				recordsDeleted++;

			}

			this.commandRepository.deleteAll(commandsToDelete);

			return (ResponseEntity.ok().body("Deleted " + recordsDeleted + " commands from DB."));

		}

		// HACER QUE EL FRONT RECIBA UN AVISO AL INTENTAR BORRAR ESTO Y PERMITA BORRAR
		// EN CASCADA.
		catch (DataIntegrityViolationException e)
		{

			return (ResponseEntity.ok().body("ERROR EN BORRADO POR INTEGRIDAD. \n" + e.getMessage()));
		}
	}

	// TODO error handling , response type by content etc..
	@PostMapping(value = "/actions-page")
	public ResponseEntity<?> getActionsPage(@PageableDefault(page = 0, size = 15) Pageable pageable)
	{
		log.debug("Call to fetch actions page received");

		Page<ActionDto> actionsPage = this.actionRepositories.findAllActionstoDto(pageable);

		return ResponseEntity.ok().body(actionsPage);
	}

	@DeleteMapping(value = "/actions")
	public ResponseEntity<?> deleteActions(@RequestBody List<ActionDto> actionsList)
	{
		try
		{
			log.debug("Call to delete actions received");

			List<Action> actionsToDelete = new ArrayList<>();
			int recordsDeleted = 0;

			for (ActionDto action : actionsList)
			{

				Action actionEntity = this.actionRepositories.findById(action.getActionName()).get();

				actionsToDelete.add(actionEntity);
				recordsDeleted++;

			}

			this.actionRepositories.deleteAll(actionsToDelete);

			return (ResponseEntity.ok().body("Deleted " + recordsDeleted + " actions from DB."));

		}

		// HACER QUE EL FRONT RECIBA UN AVISO AL INTENTAR BORRAR ESTO Y PERMITA BORRAR
		// EN CASCADA.
		catch (DataIntegrityViolationException e)
		{

			return (ResponseEntity.ok().body("ERROR EN BORRADO POR INTEGRIDAD. \n" + e.getMessage()));
		}
	}

	@PostMapping("/server-events")
	public ResponseEntity<?> getEventsPAge(@RequestBody(required = false) EventFilterObject eventFilterObject,
			@PageableDefault(page = 0, size = 10) Pageable pageable)
	{
		log.debug("Call to fetch server event page received");
		log.debug("Classroom: {}", eventFilterObject.getClassroomName());
		log.debug("Floor: {}", eventFilterObject.getFloorName());
		log.debug("Model: {}", eventFilterObject.getModelName());
		log.debug("getActionStatus: {}", eventFilterObject.getActionStatus());
		log.debug("getActionName: {}", eventFilterObject.getActionName());
		log.debug("getEventId: {}", eventFilterObject.getEventId());
		log.debug("getUser: {}", eventFilterObject.getUser());
		log.debug("getDateTime: {}", eventFilterObject.getDateTime());

		Page<TableServerEventDto> pagina = this.serverEventRepository.getTableServerEventDtoPage(pageable,
				eventFilterObject.getClassroomName(), eventFilterObject.getFloorName(),
				eventFilterObject.getModelName(), eventFilterObject.getActionStatus());

		log.debug("Recuperados:" + pagina.toList().size());

		return ResponseEntity.ok().body(pagina);
	}

	@GetMapping("/general-overview")
	public ResponseEntity<?> getServerOverview()
	{

		//
		log.debug("Request for general overview received.");
		GeneralCountOverviewDto projectorOverview = new GeneralCountOverviewDto();

		projectorOverview.setNumberOfProjectors(this.projectorRepository.count());
		projectorOverview.setNumberOfActions(this.actionRepositories.count());
		projectorOverview.setNumberOfClassrooms(this.classroomRepository.count());
		projectorOverview.setNumberOfCommands(this.commandRepository.count());
		projectorOverview.setNumberOfFloors(this.floorRepository.count());
		projectorOverview.setNumberOfModels(this.projectorModelRepository.count());

		return ResponseEntity.ok().body(projectorOverview);

	}

	@GetMapping("/events-overview")
	public ResponseEntity<?> getEventsOverview()
	{

		//
		log.debug("Request for events overview received.");
		ServerEventOverviewDto serverEventOverviewDto = new ServerEventOverviewDto();

		serverEventOverviewDto.setCanceledEvents(
				this.serverEventRepository.getNumberOfEventsCountByStatus(Constants.EVENT_STATUS_CANCELED));
		serverEventOverviewDto.setCompletedEvents(
				this.serverEventRepository.getNumberOfEventsCountByStatus(Constants.EVENT_STATUS_EXECUTED));
		serverEventOverviewDto.setDeliveredEvents(
				this.serverEventRepository.getNumberOfEventsCountByStatus(Constants.EVENT_STATUS_SERVED));
		serverEventOverviewDto.setErrorEvents(
				this.serverEventRepository.getNumberOfEventsCountByStatus(Constants.EVENT_STATUS_ERROR));
		serverEventOverviewDto.setPendingEvents(
				this.serverEventRepository.getNumberOfEventsCountByStatus(Constants.EVENT_STATUS_PENDING));

		return ResponseEntity.ok().body(serverEventOverviewDto);

	}

	@GetMapping("/models-overview")
	public ResponseEntity<?> getModelsOverview()
	{

		//
		log.debug("Request for models overview received.");
		List<ModelOverviewDto> modelOverviewDtoList = new ArrayList<>();
		List<ProjectorModel> modelList = this.projectorModelRepository.findAll();

		for (ProjectorModel model : modelList)
		{

			ModelOverviewDto currentDto = new ModelOverviewDto();
			currentDto.setModelname(model.getModelName());
			currentDto.setAssociatedProjectors(model.getAssociatedProjectors().size());
			currentDto.setAssociatedCommands(model.getAssociatedCommands().size());

			modelOverviewDtoList.add(currentDto);
		}

		return ResponseEntity.ok().body(modelOverviewDtoList);

	}

}
