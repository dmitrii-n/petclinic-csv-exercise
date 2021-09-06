package org.springframework.samples.petclinic.rest.importcsv;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/import")
public class ImportCSV {

    @Autowired
    private ClinicService clinicService;

    @PreAuthorize("hasRole(@roles.OWNER_ADMIN)")
    @RequestMapping(value = "importPets",
        method = RequestMethod.POST,
        consumes = "text/plain",
        produces = "application/json")
    public ResponseEntity<List<Pet>> importPets(@RequestBody String csv) {
        // the method has to renamed - it doesn't just import, it also deletes
        List<Pet> petsToAdd = new LinkedList<>();
        List<Pet> petsToDelete = new LinkedList<>();
        for (String line : csv.split("\\r?\\n")) {
            String[] fields = line.split(";");

            if (fields.length < 5) {
                return errorResponse("not enough values");
            }

            Pet pet = new Pet();
            pet.setName(fields[0]);
            SettingResponse settingResponse;

            settingResponse = setDateOfBirth(pet, fields[1]);
            if (!settingResponse.isSuccessful) {
                return errorResponse(settingResponse.errorMessage);
            }

            settingResponse = setPetType(pet, fields[2]);
            if (!settingResponse.isSuccessful) {
                return errorResponse(settingResponse.errorMessage);
            }

            settingResponse = setOwner(pet, fields[3]);
            if (!settingResponse.isSuccessful) {
                return errorResponse(settingResponse.errorMessage);
            }

            String action = fields[4];
            if (action.equalsIgnoreCase("add")) {
                petsToAdd.add(pet);
            } else if (action.equalsIgnoreCase("delete")) {
                petsToDelete.add(pet);
            } else {
                return errorResponse("invalid action");
            }
        }

        for (Pet p : petsToAdd) {
            clinicService.savePet(p);
        }

        for (Pet p : petsToDelete) {
            deletePet(p);
        }

        return new ResponseEntity<>(
            Stream.concat(
                petsToAdd.stream(),
                petsToDelete.stream()
            ).collect(Collectors.toList()),
            HttpStatus.OK
        );
    }

    private static class SettingResponse {
        public boolean isSuccessful;
        public String errorMessage;

        public SettingResponse() {
            this.isSuccessful = true;
            this.errorMessage = null;
        }

        public SettingResponse(String errorMessage) {
            this.isSuccessful = false;
            this.errorMessage = errorMessage;
        }
    }

    private ResponseEntity<List<Pet>> errorResponse(String headerValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("errors", headerValue);
        return new ResponseEntity<>(headers, HttpStatus.BAD_REQUEST);
    }

    private SettingResponse setDateOfBirth(Pet pet, String dateOfBirth) {
        try {
            pet.setBirthDate((new SimpleDateFormat("yyyy-MM-dd")).parse(dateOfBirth));
            return new SettingResponse();
        } catch (ParseException e) {
            return new SettingResponse("date " + dateOfBirth + " not valid");
        }
    }

    private SettingResponse setPetType(Pet pet, String petTypeName) {
        PetType petType = findPetTypeByName(petTypeName);
        if (petType != null) {
            pet.setType(petType);
            return new SettingResponse();
        }
        return new SettingResponse("type " + petTypeName + " not valid");
    }

    private SettingResponse setOwner(Pet pet, String ownerName) {
        List<Owner> matchingOwners = findOwnersByName(ownerName);
        if (matchingOwners.size() == 1) {
            pet.setOwner(matchingOwners.iterator().next());
            return new SettingResponse();
        } else if (matchingOwners.size() > 1) {
            return new SettingResponse("Owner not unique");
        } else {
            return new SettingResponse("Owner not found");
        }
    }

    private PetType findPetTypeByName(String name) {
        return clinicService.findPetTypes().stream().filter(
            petType -> name.equals(petType.getName().toLowerCase())
        ).findFirst().orElse(null);
    }

    private List<Owner> findOwnersByName(String name) {
        return clinicService.findAllOwners()
            .stream()
            .filter(o -> o.getLastName().equals(name))
            .collect(Collectors.toList());
    }

    private void deletePet(Pet pet) {
        for (Pet existingPet : pet.getOwner().getPets()) {
            if (equalPets(pet, existingPet)) {
                clinicService.deletePet(existingPet);
            }
        }
    }

    private boolean equalPets(Pet pet1, Pet pet2) {
        if (!pet1.getName().equals(pet2.getName())) {
            return false;
        }
        if (!pet1.getType().getId().equals(pet2.getType().getId())) {
            return false;
        }
        if (!pet1.getBirthDate().equals(pet2.getBirthDate())) {
            return false;
        }
        return true;
    }
}
