This sample repository shows how to use an external OCR provider (in this case [Amazon Textract](https://aws.amazon.com/textract/)) and upload the resulting PDFs into [tagtog](https://tagtog.net). The code is written in Java (11).

<img width="500" alt="Screen Shot 2021-04-20 at 18 54 03" src="https://user-images.githubusercontent.com/102431/115435303-fbedc080-a209-11eb-98f8-fdf18e4b928d.png">

This code starts from an [Amazon Textract Tutorial](https://aws.amazon.com/blogs/machine-learning/generating-searchable-pdfs-from-scanned-documents-automatically-with-amazon-textract/) ([original code](https://github.com/aws-samples/amazon-textract-searchable-pdf)) to OCR input files (PDFs or images) and convert them into "searchable PDFs" (i.e. PDFs with embedded text). These "searchable PDFs" are exactly what we want to upload to tagtog to then annotate them using [tagtog Native PDF](https://docs.tagtog.net/pdf-annotation-tool.html).

This respository adds additional utilities (e.g. traversing & processing recursively given directories) and using the [tagtog Documents APIs](https://docs.tagtog.net/API_documents_v1.html) to upload the results to a given tagtog project. Http requests are done with java, [Apache HttpClient (4.5)](https://hc.apache.org/httpcomponents-client-4.5.x/index.html).

The main entry point is [DemoTagtogOcr.java](https://github.com/tagtog/java-ocr-amazon-textract-searchable-pdf/blob/master/src/SearchablePDF/src/main/java/DemoTagtogOcr.java#L101). The main ingredients of the code are 3:

1. [Call Amazon Textract API](https://github.com/tagtog/java-ocr-amazon-textract-searchable-pdf/blob/master/src/SearchablePDF/src/main/java/DemoPdfFromLocalPdf.java#L18)
2. [Translating the JSON output from Amazon Textract into a "searchable PDF" (with java pdfbox)](https://github.com/tagtog/java-ocr-amazon-textract-searchable-pdf/blob/master/src/SearchablePDF/src/main/java/DemoPdfFromLocalPdf.java#L45)
3. [Call the tagtog API to upload documents](https://github.com/tagtog/java-ocr-amazon-textract-searchable-pdf/blob/master/src/SearchablePDF/src/main/java/DemoTagtogOcr.java#L161)


## Compile

```shell
git clone https://github.com/tagtog/java-ocr-amazon-textract-searchable-pdf.git
cd java-ocr-amazon-textract-searchable-pdf/src/SearchablePDF/

./compile.sh
```

## Run

```shell
# Set your tagtog credentials
export TAGTOG_USERNAME=???
export TAGTOG_PASSWORD=???
# export TAGTOG_DOMAIN=??? # optionally, override the tagtog domain, for example if you are running tagtog OnPremises

time ./run.sh MY_TAGTOG_OWNERNAME MY_TAGTOG_PROJECT MY_TAGTOG_FOLDER ...inputFilesOrDirectories
```


## Setup Amazon Textract

If you are new to AWS or unsure about the details, [this is the complete AWS guide to get started with Amazon Textract](https://docs.aws.amazon.com/textract/latest/dg/getting-started.html).

In short, what you need is:

1. Make sure you have an IAM user with `AmazonTextractFullAccess` permissions & with an access key.
2. Configure your local aws credentials, [with the `[default]` role pointing to that IAM user and also set your desired `region`](https://docs.aws.amazon.com/textract/latest/dg/setup-awscli-sdk.html).


## 🍃 Sample tagtog Project

Using this very same code, we OCR'ed the [FUNSD dataset](https://guillaumejaume.github.io/FUNSD/) and uploaded the results into the tagtog sample project [tagtog/FUNSD-OCRed](https://www.tagtog.net/tagtog/FUNSD-OCRed/pool) 😃.

We exactly ran (last update on 2021-04-20):

```shell
time ./run.sh tagtog FUNSD-OCRed testing_data ~/Downloads/dataset/testing_data/  # took around ~2m; 50 docs in total
time ./run.sh tagtog FUNSD-OCRed training_data ~/Downloads/dataset/training_data/  # took around ~6m; 149 docs in total
```

[These are some sample annotated documents in tagtog](https://www.tagtog.net/tagtog/FUNSD-OCRed/-search/entity%3ASampleEntity1%3A*).


## Notes

The [original demo code](https://github.com/aws-samples/amazon-textract-searchable-pdf/raw/master/src/SearchablePDF/documents/SampleOutput.pdf) tends to create oversized PDFs and to write the embedded character offsets a little bit below the actual (visual) positions. These details can be tweaked and of course depend on the used OCR software.