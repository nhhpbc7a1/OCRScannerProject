package hcmute.edu.vn.ocrscannerproject.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;

/**
 * Provider class for generating sample scanned documents for demonstration purposes.
 * This class creates sample documents with generated images and text content.
 */
public class SampleDataProvider {
    private static final String TAG = "SampleDataProvider";
    private static final String SAMPLE_USER = "sample_user";
    
    private static SampleDataProvider instance;
    private final Context context;
    private final ScannedDocumentRepository repository;
    
    private SampleDataProvider(Context context) {
        this.context = context.getApplicationContext();
        this.repository = ScannedDocumentRepository.getInstance(context);
    }
    
    public static synchronized SampleDataProvider getInstance(Context context) {
        if (instance == null) {
            instance = new SampleDataProvider(context);
        }
        return instance;
    }
    
    /**
     * Initializes the app with sample data if no documents exist yet.
     * @return true if sample data was created, false if documents already existed
     */
    public boolean initializeSampleDataIfNeeded() {
        // Check if we already have documents
        List<ScannedDocument> existingDocuments = repository.getAllDocuments();
        if (!existingDocuments.isEmpty()) {
            Log.d(TAG, "Sample data not created: " + existingDocuments.size() + " documents already exist");
            return false;
        }
        
        // Create and add sample documents
        List<ScannedDocument> sampleDocuments = createSampleDocuments();
        for (ScannedDocument document : sampleDocuments) {
            repository.addDocument(document);
        }
        
        Log.d(TAG, "Created " + sampleDocuments.size() + " sample documents");
        return true;
    }
    
    /**
     * Creates a list of sample documents with different types and content
     */
    private List<ScannedDocument> createSampleDocuments() {
        List<ScannedDocument> documents = new ArrayList<>();
        
        // 1. Invoice document with multiple pages
        ScannedDocument invoice = new ScannedDocument(
                "Electric Bill Invoice", 
                SAMPLE_USER,
                createSampleImages("Invoice", 2)
        );
        invoice.setType("PDF");
        invoice.setExtractedText(
                "ELECTRIC POWER INVOICE\n\n" +
                "Invoice #: INV-2023-0458\n" +
                "Date: 10/11/2023\n\n" +
                "Customer: John Smith\n" +
                "Account: 1234567890\n\n" +
                "Previous Reading: 4582 kWh\n" +
                "Current Reading: 4801 kWh\n" +
                "Usage: 219 kWh\n\n" +
                "Rate: $0.12/kWh\n" +
                "Energy Charge: $26.28\n" +
                "Service Fee: $9.99\n" +
                "Tax (8%): $2.90\n\n" +
                "Total Due: $39.17\n\n" +
                "Due Date: 25/11/2023\n" +
                "Please pay online at www.electriccompany.com"
        );
        documents.add(invoice);
        
        // 2. Receipt document
        ScannedDocument receipt = new ScannedDocument(
                "Grocery Store Receipt",
                SAMPLE_USER,
                createSampleImages("Receipt", 1)
        );
        receipt.setType("Image");
        receipt.setExtractedText(
                "FRESH MARKET GROCERY\n" +
                "123 Main Street\n" +
                "Phone: (555) 123-4567\n\n" +
                "Receipt #: 8752\n" +
                "Date: 15/11/2023 14:32\n\n" +
                "Apples (1kg)         $3.99\n" +
                "Milk (1L)            $2.49\n" +
                "Bread                $2.99\n" +
                "Eggs (12)            $3.49\n" +
                "Chicken Breast       $8.75\n" +
                "Pasta                $1.99\n" +
                "Tomatoes (500g)      $2.29\n\n" +
                "Subtotal:           $25.99\n" +
                "Tax (6%):            $1.56\n" +
                "TOTAL:              $27.55\n\n" +
                "PAID - CREDIT CARD\n" +
                "Thank you for shopping with us!"
        );
        documents.add(receipt);
        
        // 3. Business card
        ScannedDocument businessCard = new ScannedDocument(
                "Tech Company Business Card",
                SAMPLE_USER,
                createSampleImages("BusinessCard", 1)
        );
        businessCard.setType("Image");
        businessCard.setExtractedText(
                "INNOVATIVE TECH SOLUTIONS\n\n" +
                "Sarah Johnson\n" +
                "Senior Software Engineer\n\n" +
                "sarah.johnson@innovativetech.com\n" +
                "+1 (555) 987-6543\n\n" +
                "www.innovativetech.com\n\n" +
                "123 Tech Boulevard\n" +
                "San Francisco, CA 94105"
        );
        documents.add(businessCard);
        
        // 4. Article or document with text
        ScannedDocument article = new ScannedDocument(
                "AI Research Paper",
                SAMPLE_USER,
                createSampleImages("Article", 3)
        );
        article.setType("Text");
        article.setExtractedText(
                "ADVANCES IN ARTIFICIAL INTELLIGENCE\n\n" +
                "Abstract\n" +
                "This paper explores recent developments in artificial intelligence, focusing on machine learning algorithms and their applications in various domains. We present a comprehensive analysis of current state-of-the-art models and discuss their limitations and potential future directions.\n\n" +
                "1. Introduction\n" +
                "Artificial Intelligence (AI) has seen remarkable progress in recent years, transforming numerous industries and aspects of daily life. From healthcare to transportation, AI technologies are increasingly being integrated into systems that make critical decisions affecting human welfare and safety.\n\n" +
                "2. Background\n" +
                "The field of AI has evolved significantly since its inception in the 1950s. Early rule-based systems have given way to more sophisticated approaches based on statistical learning and neural networks. The advent of deep learning, in particular, has led to breakthroughs in areas such as computer vision, natural language processing, and reinforcement learning.\n\n" +
                "3. Methodology\n" +
                "Our research methodology involved a systematic review of literature published between 2018 and 2023, focusing on peer-reviewed journals and conference proceedings. We analyzed over 500 papers and identified key trends and innovations in AI research."
        );
        documents.add(article);
        
        // 5. Form or application
        ScannedDocument form = new ScannedDocument(
                "Job Application Form",
                SAMPLE_USER,
                createSampleImages("Form", 2)
        );
        form.setType("PDF");
        form.setExtractedText(
                "JOB APPLICATION FORM\n\n" +
                "Position Applied For: Software Developer\n\n" +
                "PERSONAL INFORMATION\n" +
                "Name: Michael Chen\n" +
                "Address: 456 Oak Street, Apt 7B\n" +
                "City: Boston\n" +
                "State: MA\n" +
                "ZIP: 02115\n" +
                "Phone: (555) 234-5678\n" +
                "Email: michael.chen@email.com\n\n" +
                "EDUCATION\n" +
                "University: Massachusetts Institute of Technology\n" +
                "Degree: Bachelor of Science in Computer Science\n" +
                "Graduation Year: 2021\n" +
                "GPA: 3.8/4.0\n\n" +
                "WORK EXPERIENCE\n" +
                "Company: Tech Innovations Inc.\n" +
                "Position: Junior Developer\n" +
                "Dates: June 2021 - Present\n" +
                "Responsibilities: Developed web applications using React and Node.js, collaborated with design team on UI/UX improvements, implemented RESTful APIs."
        );
        documents.add(form);
        
        return documents;
    }
    
    /**
     * Creates sample images for documents
     * @param prefix Prefix for the image name
     * @param count Number of images to create
     * @return List of paths to the created images
     */
    private List<String> createSampleImages(String prefix, int count) {
        List<String> imagePaths = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String imagePath = createSampleImage(prefix + "_" + (i + 1));
            if (imagePath != null) {
                imagePaths.add(imagePath);
            }
        }
        
        return imagePaths;
    }
    
    /**
     * Creates a single sample image with text
     * @param name Name for the image file
     * @return Path to the created image, or null if creation failed
     */
    private String createSampleImage(String name) {
        try {
            // Create directory if it doesn't exist
            File outputDir = new File(context.getFilesDir(), "sample_images");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            // Create a unique filename
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = name + "_" + timeStamp + ".jpg";
            File outputFile = new File(outputDir, fileName);
            
            // Create a bitmap with text
            Bitmap bitmap = createTextBitmap(name, 1000, 1400);
            
            // Save the bitmap to file
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            
            bitmap.recycle();
            return outputFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating sample image: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Creates a bitmap with text content
     */
    private Bitmap createTextBitmap(String title, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Fill background
        canvas.drawColor(Color.WHITE);
        
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(50);
        paint.setAntiAlias(true);
        
        // Draw title
        canvas.drawText(title, 50, 100, paint);
        
        // Draw a border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        canvas.drawRect(20, 20, width - 20, height - 20, paint);
        
        // Draw some random content
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(30);
        
        String[] sampleTexts = {
                "This is a sample document for demonstration purposes.",
                "OCR Scanner can extract text from images like this one.",
                "The text can be edited, copied, and saved.",
                "You can also share documents with others.",
                "Multiple pages can be scanned into a single document."
        };
        
        Random random = new Random();
        for (int i = 0; i < 15; i++) {
            String text = sampleTexts[random.nextInt(sampleTexts.length)];
            canvas.drawText(text, 50, 200 + i * 60, paint);
        }
        
        // Draw some lines
        for (int i = 0; i < 5; i++) {
            int y = 600 + i * 100;
            canvas.drawLine(50, y, width - 50, y, paint);
        }
        
        return bitmap;
    }
} 