-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- Host: localhost    Database: onlinehealth
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `admin`
--

DROP TABLE IF EXISTS `admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin`
--

LOCK TABLES `admin` WRITE;
/*!40000 ALTER TABLE `admin` DISABLE KEYS */;
INSERT INTO `admin` VALUES (1,'admin','$2a$10$YourHashedPasswordHere');
/*!40000 ALTER TABLE `admin` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `appointments`
--

DROP TABLE IF EXISTS `appointments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `appointments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `patient_id` int NOT NULL,
  `counsellor_id` int NOT NULL,
  `appointment_date` date NOT NULL,
  `appointment_time` time NOT NULL,
  `reason` text,
  `payment_id` int DEFAULT NULL,
  `payment_status` varchar(20) DEFAULT 'pending',
  `status` enum('pending','confirmed','completed','cancelled') DEFAULT 'pending',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `patient_id` (`patient_id`),
  KEY `counsellor_id` (`counsellor_id`),
  KEY `payment_id` (`payment_id`),
  CONSTRAINT `appointments_ibfk_1` FOREIGN KEY (`patient_id`) REFERENCES `users` (`id`),
  CONSTRAINT `appointments_ibfk_2` FOREIGN KEY (`counsellor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `appointments_ibfk_3` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `appointments`
--

LOCK TABLES `appointments` WRITE;
/*!40000 ALTER TABLE `appointments` DISABLE KEYS */;
INSERT INTO `appointments` VALUES (41,51,54,'2026-03-20','10:00:00','stress and angeity',34,'paid','confirmed','2026-03-17 07:08:23');
/*!40000 ALTER TABLE `appointments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `chat_messages`
--

DROP TABLE IF EXISTS `chat_messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_messages` (
  `id` int NOT NULL AUTO_INCREMENT,
  `sender_id` int NOT NULL,
  `receiver_id` int NOT NULL,
  `message` text NOT NULL,
  `is_read` tinyint(1) DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `sender_id` (`sender_id`),
  KEY `receiver_id` (`receiver_id`),
  CONSTRAINT `chat_messages_ibfk_1` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chat_messages_ibfk_2` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=73 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `chat_messages`
--

LOCK TABLES `chat_messages` WRITE;
/*!40000 ALTER TABLE `chat_messages` DISABLE KEYS */;
INSERT INTO `chat_messages` VALUES (55,64,56,'h',0,'2026-02-20 12:17:12'),(56,58,64,'hiii',0,'2026-02-21 03:24:19'),(57,51,50,'hiii',0,'2026-02-21 05:39:36'),(58,51,50,'heloo',0,'2026-02-21 05:40:00'),(59,51,53,'fff',0,'2026-02-24 10:12:22'),(60,51,58,'hiii',1,'2026-02-24 10:34:32'),(61,51,58,'hello',1,'2026-02-24 10:34:49'),(62,51,58,'hii',1,'2026-02-24 11:56:45'),(63,51,58,'hee',1,'2026-02-24 11:57:59'),(64,51,58,'hello santanu',1,'2026-02-24 11:58:32'),(65,51,58,'hello doctor',1,'2026-02-24 11:59:27'),(66,51,54,'HE;;',0,'2026-02-24 12:27:58'),(67,51,53,'hiii',0,'2026-02-25 08:19:46'),(68,58,51,'hee',1,'2026-02-25 08:20:43'),(69,58,51,'hello',1,'2026-02-25 08:20:57'),(70,58,51,'hii',1,'2026-02-27 06:11:50'),(71,51,58,'hoii',1,'2026-02-27 06:13:05'),(72,51,58,'hiii',1,'2026-02-27 06:13:21');
/*!40000 ALTER TABLE `chat_messages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `counsellors`
--

DROP TABLE IF EXISTS `counsellors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `counsellors` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `qualification` varchar(255) DEFAULT NULL,
  `fee` decimal(10,2) DEFAULT '0.00',
  `status` enum('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
  `registered_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `counsellors`
--

LOCK TABLES `counsellors` WRITE;
/*!40000 ALTER TABLE `counsellors` DISABLE KEYS */;
INSERT INTO `counsellors` VALUES (1,'Dr. Sharma','drsharma@gmail.com','',NULL,NULL,0.00,'PENDING','2026-03-16 16:19:11');
/*!40000 ALTER TABLE `counsellors` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feedback`
--

DROP TABLE IF EXISTS `feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feedback` (
  `id` int NOT NULL AUTO_INCREMENT,
  `patient_id` int NOT NULL,
  `counsellor_id` int NOT NULL,
  `appointment_id` int DEFAULT NULL,
  `rating_overall` tinyint NOT NULL,
  `rating_communication` tinyint DEFAULT '0',
  `rating_empathy` tinyint DEFAULT '0',
  `rating_helpfulness` tinyint DEFAULT '0',
  `tags` varchar(500) DEFAULT NULL,
  `comment` text,
  `recommend` varchar(10) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `appointment_id` (`appointment_id`),
  KEY `patient_id` (`patient_id`),
  KEY `counsellor_id` (`counsellor_id`),
  CONSTRAINT `feedback_ibfk_1` FOREIGN KEY (`patient_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `feedback_ibfk_2` FOREIGN KEY (`counsellor_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `feedback_ibfk_3` FOREIGN KEY (`appointment_id`) REFERENCES `appointments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feedback`
--

LOCK TABLES `feedback` WRITE;
/*!40000 ALTER TABLE `feedback` DISABLE KEYS */;
INSERT INTO `feedback` VALUES (1,51,55,NULL,5,3,2,5,'Professional',NULL,'true','2026-02-25 09:16:12'),(2,51,54,NULL,5,5,3,4,'Non-Judgmental',NULL,'true','2026-02-25 23:40:45'),(3,58,58,NULL,5,5,2,4,'Caring,Professional,Non-Judgmental,Clear Explanations',NULL,'true','2026-02-26 05:40:49'),(4,51,50,NULL,5,4,2,5,'Professional,Very Helpful,Good Listener',NULL,'true','2026-02-27 06:38:11'),(5,51,54,NULL,5,5,5,5,'Non-Judgmental,Clear Explanations',NULL,'true','2026-02-27 08:16:07'),(6,51,50,NULL,1,1,1,1,'Very Helpful','not show good','true','2026-02-27 08:18:14'),(7,51,55,NULL,5,5,5,1,'Caring','nice','true','2026-02-27 08:22:03');
/*!40000 ALTER TABLE `feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notifications`
--

DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `title` varchar(100) NOT NULL,
  `message` text NOT NULL,
  `type` varchar(30) DEFAULT 'appointment',
  `is_read` tinyint(1) DEFAULT '0',
  `appointment_id` int DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `notifications_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notifications`
--

LOCK TABLES `notifications` WRITE;
/*!40000 ALTER TABLE `notifications` DISABLE KEYS */;
INSERT INTO `notifications` VALUES (1,51,'🎉 Appointment Confirmed!','Great news! Your appointment with khushi on 2026-03-17 at 16:00 has been confirmed. Please be available on time.','appointment',0,36,'2026-03-13 00:51:05'),(2,51,'❌ Appointment Cancelled','Your appointment with khushi on 2026-03-17 at 16:00 has been cancelled by the counsellor. Please book a new slot.','appointment',0,36,'2026-03-13 01:43:06'),(3,51,'🎉 Appointment Confirmed!','Great news! Your appointment with khushi on 2026-03-19 at 17:00 has been confirmed. Please be available on time.','appointment',0,39,'2026-03-16 20:50:57'),(4,51,'Appointment Completed','Your appointment with khushi on 2026-03-19 at 17:00 has been marked as completed. We hope your session went well!','appointment',0,39,'2026-03-16 21:08:49'),(5,51,'Appointment Confirmed!','Great news! Your appointment with Dr. Amit Verma on 2026-03-20 at 10:00 has been confirmed. Please be available on time.','appointment',0,41,'2026-03-17 07:50:29');
/*!40000 ALTER TABLE `notifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `patients`
--

DROP TABLE IF EXISTS `patients`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patients` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `role` enum('patient') DEFAULT 'patient',
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `patients`
--

LOCK TABLES `patients` WRITE;
/*!40000 ALTER TABLE `patients` DISABLE KEYS */;
INSERT INTO `patients` VALUES (1,'Test Patient','test@gmail.com',NULL,'123456','2026-02-19 12:51:52','patient');
/*!40000 ALTER TABLE `patients` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payments`
--

DROP TABLE IF EXISTS `payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `appointment_id` int DEFAULT NULL,
  `amount` decimal(10,2) NOT NULL,
  `payment_method` enum('credit_card','debit_card','paypal','cash','fake','offline','online') NOT NULL,
  `status` enum('pending','completed','failed','refunded') DEFAULT 'pending',
  `transaction_id` varchar(255) DEFAULT NULL,
  `payment_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `patient_id` int DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `transaction_id` (`transaction_id`),
  KEY `appointment_id` (`appointment_id`),
  KEY `fk_payments_patient` (`patient_id`),
  CONSTRAINT `payments_ibfk_1` FOREIGN KEY (`appointment_id`) REFERENCES `appointments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payments`
--

LOCK TABLES `payments` WRITE;
/*!40000 ALTER TABLE `payments` DISABLE KEYS */;
INSERT INTO `payments` VALUES (34,41,2000.00,'fake','completed',NULL,'2026-03-17 07:08:23',51,'2026-03-17 07:08:23','2026-03-17 07:08:23');
/*!40000 ALTER TABLE `payments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `prescriptions`
--

DROP TABLE IF EXISTS `prescriptions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `prescriptions` (
  `id` int NOT NULL AUTO_INCREMENT,
  `patient_id` int NOT NULL,
  `counsellor_id` int NOT NULL,
  `appointment_id` int DEFAULT NULL,
  `medication` varchar(255) NOT NULL,
  `dosage` varchar(100) DEFAULT NULL,
  `frequency` varchar(100) DEFAULT NULL,
  `duration` varchar(100) DEFAULT NULL,
  `instructions` text,
  `prescribed_date` date DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_path` varchar(500) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `patient_id` (`patient_id`),
  KEY `counsellor_id` (`counsellor_id`),
  KEY `appointment_id` (`appointment_id`),
  CONSTRAINT `prescriptions_ibfk_1` FOREIGN KEY (`patient_id`) REFERENCES `users` (`id`),
  CONSTRAINT `prescriptions_ibfk_2` FOREIGN KEY (`counsellor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `prescriptions_ibfk_3` FOREIGN KEY (`appointment_id`) REFERENCES `appointments` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `prescriptions`
--

LOCK TABLES `prescriptions` WRITE;
/*!40000 ALTER TABLE `prescriptions` DISABLE KEYS */;
INSERT INTO `prescriptions` VALUES (1,51,58,17,'paracetamol','3','Twice daily','4','rest','2026-02-24',NULL,NULL,'2026-02-24 10:29:52'),(2,51,58,NULL,'paracetamol mmmmm','5','Three times daily','twice','always being calm','2026-03-17',NULL,NULL,'2026-03-17 08:09:57');
/*!40000 ALTER TABLE `prescriptions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `contact` varchar(15) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `address` text,
  `blood_group` varchar(10) DEFAULT NULL,
  `emergency_contact` varchar(15) DEFAULT NULL,
  `role` enum('admin','counsellor','patient') DEFAULT 'patient',
  `specialty` varchar(100) DEFAULT NULL,
  `experience_years` int DEFAULT '0',
  `consultation_fee` decimal(10,2) DEFAULT '1000.00',
  `status` enum('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
  `is_allowed` tinyint(1) DEFAULT '1',
  `specialization` varchar(100) DEFAULT NULL,
  `experience` varchar(50) DEFAULT NULL,
  `languages` varchar(100) DEFAULT NULL,
  `bio` text,
  `active` tinyint DEFAULT '1',
  `photo_url` varchar(255) DEFAULT NULL,
  `qualification` varchar(100) DEFAULT NULL,
  `about` text,
  `registration_number` varchar(50) DEFAULT NULL,
  `degrees` text,
  `college` varchar(100) DEFAULT NULL,
  `certifications` text,
  `memberships` text,
  `rating` decimal(2,1) DEFAULT '0.0',
  `total_reviews` int DEFAULT '0',
  `consultation_mode` varchar(20) DEFAULT 'both',
  `profile_pic` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (50,'Khushboo ','khushboo04806@gmail.com','1bbd886460827015e5d605ed44252251','9096296534',21,NULL,NULL,NULL,'counsellor',NULL,0,1000.00,'APPROVED',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(51,'santanu','santanu1@gmail.com','1bbd886460827015e5d605ed44252251','9096296534',21,NULL,NULL,NULL,'patient',NULL,0,1000.00,'PENDING',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(52,'santanu','santanu2@gmail.com','1bbd886460827015e5d605ed44252251','9096296534',21,NULL,NULL,NULL,'patient',NULL,0,1000.00,'PENDING',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(53,'Dr. Riya Sharma','riya@example.com','1bbd886460827015e5d605ed44252251',NULL,NULL,NULL,NULL,NULL,'counsellor','Clinical Psychologist',8,1500.00,'APPROVED',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(54,'Dr. Amit Verma','amit@example.com','1bbd886460827015e5d605ed44252251',NULL,NULL,NULL,NULL,NULL,'counsellor','Psychiatrist',12,2000.00,'APPROVED',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(55,'Dr. Shweta Rao','shweta@example.com','1bbd886460827015e5d605ed44252251',NULL,NULL,NULL,NULL,NULL,'counsellor','Counseling Psychologist',6,1200.00,'APPROVED',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(56,'Dr. Sanjay Kulkarni','sanjay@example.com','1bbd886460827015e5d605ed44252251',NULL,NULL,NULL,NULL,NULL,'counsellor','Trauma Specialist',10,1800.00,'APPROVED',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(58,'khushi','khushi1@gmail.com','1bbd886460827015e5d605ed44252251','9156272870',21,NULL,NULL,NULL,'counsellor',NULL,0,1000.00,'APPROVED',1,'Cognitive Behavioural Therapy','5','english','4',1,'1772177586636_WhatsApp Image 2026-02-27 at 12.59.49 PM.jpeg',NULL,'4',NULL,NULL,NULL,NULL,NULL,0.0,0,'both','1772177586636_WhatsApp Image 2026-02-27 at 12.59.49 PM.jpeg'),(60,'santanu','santanu3@gmail.com','d27d320c27c3033b7883347d8beca317','9096296534',21,NULL,NULL,NULL,'patient',NULL,0,1000.00,'PENDING',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(62,'santanu','santanu4@gmail.com','1bbd886460827015e5d605ed44252251','9096296534',21,NULL,NULL,NULL,'patient',NULL,0,1000.00,'PENDING',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(63,'khushboopal','khushboopal1@gmail.com','1bbd886460827015e5d605ed44252251','9096296534',21,NULL,NULL,NULL,'admin',NULL,0,1000.00,'PENDING',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(64,'neha','neha1@gmail.com','1bbd886460827015e5d605ed44252251','9096296534',21,NULL,NULL,NULL,'patient',NULL,0,1000.00,'PENDING',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(65,'Khushboo ','khushboo1@gmail.com','1bbd886460827015e5d605ed44252251','9156272870',21,NULL,NULL,NULL,'admin',NULL,0,1000.00,'PENDING',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL),(66,'soham','soham@gmail.com','81dc9bdb52d04dc20036dbd8313ed055','1234567890',18,NULL,NULL,NULL,'patient',NULL,0,1000.00,'PENDING',1,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0.0,0,'both',NULL);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `video_calls`
--

DROP TABLE IF EXISTS `video_calls`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `video_calls` (
  `id` int NOT NULL AUTO_INCREMENT,
  `room_id` varchar(100) NOT NULL,
  `caller_id` int NOT NULL,
  `receiver_id` int NOT NULL,
  `call_type` enum('audio','video') DEFAULT 'video',
  `status` enum('ringing','active','ended','rejected','missed') DEFAULT 'ringing',
  `start_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `end_time` timestamp NULL DEFAULT NULL,
  `duration` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `room_id` (`room_id`),
  KEY `idx_room` (`room_id`),
  KEY `idx_caller` (`caller_id`),
  KEY `idx_receiver` (`receiver_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `video_calls_ibfk_1` FOREIGN KEY (`caller_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `video_calls_ibfk_2` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `video_calls`
--

LOCK TABLES `video_calls` WRITE;
/*!40000 ALTER TABLE `video_calls` DISABLE KEYS */;
/*!40000 ALTER TABLE `video_calls` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-03-17 17:34:20
