"""

 DISCLAIMER: This code is adapted from:
        https://www.kaggle.com/code/rid17pawar/semantic-search-using-mean-of-vectors

However, we change some of the parts according to our needs in CENG596 project.
In the end, we save 2 JSON files, to match with the format used in our source code.
"""

import os
import json


def load_data(path):
    
    #_____________ Read data from CISI.ALL file and store in dictinary ________________
    with open(os.path.join(path, 'CISI.ALL')) as f:
        lines = ""
        for l in f.readlines():
            lines += "\n" + l.strip() if l.startswith(".") else " " + l.strip()
        lines = lines.lstrip("\n").split("\n")
 
    doc_set = {}
    doc_id = ""
    doc_text = ""
    doc_title  = ""
    for l in lines:
        if l.startswith(".I"):
            doc_id = l.split(" ")[1].strip()
        elif l.startswith(".X"):
            doc_set[doc_id] = (doc_title, doc_text)
            doc_id = ""
            doc_text = ""
            doc_title  = ""
        elif l.startswith(".T"):        # Store title
            doc_title = l.strip()[3:]
            
        elif l.startswith(".W"):        # Store text
            print(l)
            doc_text += l.strip()[3:]

    print(f"Number of documents = {len(doc_set)}")
    print(doc_set["1"])
    
    #_____________ Read data from CISI.QRY file and store in dictinary ________________
    
    with open(os.path.join(path, 'CISI.QRY')) as f:
        lines = ""
        for l in f.readlines():
            lines += "\n" + l.strip() if l.startswith(".") else " " + l.strip()
        lines = lines.lstrip("\n").split("\n")
          
    qry_set = {}
    qry_id = ""
    for l in lines:
        if l.startswith(".I"):
            qry_id = l.split(" ")[1].strip()
        elif l.startswith(".W"):
            qry_set[qry_id] = l.strip()[3:]
            qry_id = ""

    print(f"\n\nNumber of queries = {len(qry_set)}")
    print(qry_set["1"])
    
    
    #_____________ Read data from CISI.REL file and store in dictinary ________________
    
    rel_set = {}
    with open(os.path.join(path, 'CISI.REL')) as f:
        for l in f.readlines():
            qry_id = l.lstrip(" ").strip("\n").split("\t")[0].split(" ")[0]
            doc_id = l.lstrip(" ").strip("\n").split("\t")[0].split(" ")[-1]

            if qry_id in rel_set:
                rel_set[qry_id].append(doc_id)
            else:
                rel_set[qry_id] = []
                rel_set[qry_id].append(doc_id)

    print(f"\n\nNumber of mappings = {len(rel_set)}")
    print(rel_set["1"])
    
    return doc_set, qry_set, rel_set


if __name__ == "__main__":
    
    doc_set, qry_set, rel_set = load_data("./CISI_Dataset")
    
    
    print("\n>> Saving {id,title, abstract,text} json...\n")
    
    # Construct CISI Dataset JSON, same format as AAPR_parsed
    cisi_data = []
    for doc_id in doc_set:
        doc_data = {}
        title_field = doc_set[doc_id][0]
        text_field = doc_set[doc_id][1]
        
        doc_data["id"] = doc_id
        doc_data["title"] = title_field
        doc_data["abstract"] = text_field
        doc_data["text"] = text_field
        
        cisi_data.append(doc_data)
        
    # Construct CISI Relations JSON
    cisi_rels = []
    for qry_id in rel_set:
        qry_data = {}
        qry_data["query_id"] = qry_id
        qry_data["query_text"] = qry_set[qry_id]
        qry_data["doc_id_list"] =  rel_set[qry_id]
        
        cisi_rels.append(qry_data)
    
        print("\nQUERY: ", qry_set[qry_id], "\n\nRELEVANT DOCS:") #, rel_set[qry_id])
        for doc_id in rel_set[qry_id]:
            
            print(doc_set[doc_id][0], "\n")
        print("\n------\n")
        #print(cisi_rels)
        #i +=1
        #if i ==3: break
    
    with open('cisi_data.json', 'w') as f:
        json.dump(cisi_data, f)
    

    with open('cisi_relations.json', 'w') as f:
        json.dump(cisi_rels, f)
    
