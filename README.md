This sample repository shows how to use an external OCR provider (in this case [Amazon Textract](https://aws.amazon.com/textract/)) and upload the resulting PDFs into [tagtog](https://tagtog.net). The code is written in Java (11).

This code starts from an [Amazon Textract Tutorial](https://aws.amazon.com/blogs/machine-learning/generating-searchable-pdfs-from-scanned-documents-automatically-with-amazon-textract/) ([original code](https://github.com/aws-samples/amazon-textract-searchable-pdf)) to OCR input files (PDFs or images) and converting them into "searchable PDFs" (i.e. PDFs with embedded text). These "searchable PDFs" are exactly what we want to upload to tagtog to then annotate them using [tagtog Native PDF](https://docs.tagtog.net/pdf-annotation-tool.html).

This respository adds additional utilities (e.g. traversing & processing recursively given directories) and using the [tagtog Documents APIs](https://docs.tagtog.net/API_documents_v1.html) to upload the results to a given tagtog project. Http requests are done with java, [Apache HttpClient (4.5)](https://hc.apache.org/httpcomponents-client-4.5.x/index.html).

The main entry point is [DemoTagtogOcr.java](https://github.com/tagtog/java-ocr-amazon-textract-searchable-pdf/blob/master/src/SearchablePDF/src/main/java/DemoTagtogOcr.java#L101).

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


# Example Project

Using this very same code, we OCR'ed the [FUNSD dataset](https://guillaumejaume.github.io/FUNSD/) and uploaded the results into the tagtog sample project [tagtog/FUNSD-OCRed](https://www.tagtog.net/tagtog/FUNSD-OCRed/pool) 😃.

We exactly ran (last update on 2021-04-20):

```shell
time ./run.sh tagtog FUNSD-OCRed testing_data ~/Downloads/dataset/testing_data/  # took around ~2m; 50 docs in total
time ./run.sh tagtog FUNSD-OCRed training_data ~/Downloads/dataset/training_data/  # took around ~6m; 149 docs in total
```

[These are some sample annotated documents in tagtog](https://www.tagtog.net/tagtog/FUNSD-OCRed/-search/entity%3ASampleEntity1%3A*).