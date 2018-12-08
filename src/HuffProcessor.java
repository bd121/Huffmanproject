import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		//1. Determine the frequency of every eight-bit character/chunk in the input file
		int[] freqCounts = readForCounts(in);
		
		if(myDebugLevel >= DEBUG_HIGH) {
	        System.out.println("Frequncy Counts: ");
	        printIntList(freqCounts);
		}
		
		//2. Create the Huffman tree for encodings
        HuffNode root = makeTreeFromCounts(freqCounts);
        
        if(myDebugLevel >= DEBUG_HIGH) {
	        System.out.println(" Huffman tree for encodings: ");
	        printNode(root, 0);
        }
        
        //3. Create the encodings for each eight-bit character chunk
        String[] codings = makeCodingsfromTree(root);
        if(myDebugLevel >= DEBUG_HIGH) {
	        System.out.println("Coding String Array: ");
	        printList(codings);
        }
        
        //4. Write the magic number and the tree to the header of the compressed file
        out.writeBits(BITS_PER_INT, HUFF_TREE);
        writeHeader(root, out);
        
        //5. Read the file again and write the encoding for each eight-bit chunk, 
        //	followed by the encoding for PSEUDO_EOF, then close the file being written
        in.reset();
        writeCompressedBits(in, out, codings);

		out.close();
	}
	
	public int[] readForCounts(BitInputStream in)
	{
		int[] counts = new int[ALPH_SIZE+1];
        while (true){
        	//read the in file by increment every eight-bit character/chunk
            int charValue = in.readBits(BITS_PER_WORD); 
            if (charValue == -1) break; //at end of the file
            else{
                counts[charValue]++; //increment the frequency of that character at that character's integer/index value
                if(myDebugLevel >= DEBUG_HIGH)
                	System.out.printf("value: %d, char: %c\n", charValue, (char)charValue);
            }
        }
        if(myDebugLevel >= DEBUG_HIGH)
        	System.out.printf("PSEUDO_EOF's value: %d\n", PSEUDO_EOF);
        if(myDebugLevel >= DEBUG_HIGH)
        	System.out.printf("ALPH_SIZE's value: %d\n", ALPH_SIZE);
        //
        counts[PSEUDO_EOF] = 1;
        
        return counts;
	}
	
    //create Huffman tree
    public HuffNode makeTreeFromCounts(int[] freqCounts){
        PriorityQueue<HuffNode> pq = new PriorityQueue<HuffNode>();
        for (int i=0; i<ALPH_SIZE+1; i++){
          if (freqCounts[i] > 0){ //only make Huffnodes of characters that actually occur in the text
            pq.add(new HuffNode(i, freqCounts[i]));
          }
        }
       
      //combine smallest-weighted characters and continue
        while(pq.size() > 1){
          HuffNode leftChild = pq.remove();
          HuffNode rightChild = pq.remove();
          //HuffNode temporaryNode = new HuffNode('\0', leftChild.myWeight + rightChild.myWeight, leftChild, rightChild);
          HuffNode temporaryNode = new HuffNode(-1, leftChild.myWeight + rightChild.myWeight, leftChild, rightChild);
          pq.add(temporaryNode);
        }
       
        HuffNode root = pq.remove();
        //HuffNode root = HuffTree.peek();
        return root;
    }	
    
    private String[] makeCodingsfromTree(HuffNode current) {
    	String[] encodings = new String[ALPH_SIZE + 1];
    	codingHelper(current, "", encodings);
        return encodings;
    }
    
    
    private void codingHelper(HuffNode current, String path, String[] encodings) {
    	
    	//base case: current node is a leaf
        if (current.myLeft == null && current.myRight == null){ 
        	
            int charIndex = current.myValue;
            encodings[charIndex] = path;
            if(myDebugLevel >= DEBUG_HIGH)
            	System.out.printf("encoding for %d is %s \n", charIndex, path);
            return;
          }
          else{
        	  codingHelper(current.myLeft, path + 0, encodings);
        	  codingHelper(current.myRight, path + 1, encodings);
          }
    }
    
    
    //write the header
    private void writeHeader(HuffNode current, BitOutputStream out){ 
      //add the HUFF_NUMBER to the beginning of the header
       
      //base case: current node is at a leaf (at a character)
      if (current.myLeft == null && current.myRight == null){ 
            out.writeBits(1,1); 
          //stores the code of the character with 9 bits
            out.writeBits(BITS_PER_WORD+1, current.myValue); 
            if(myDebugLevel >= DEBUG_HIGH)
            	System.out.printf("writing header for current node: %d \n", current.myValue);
            return;
      }
      else {

    	  out.writeBits(1,0);
    	  writeHeader(current.myLeft, out);
    	  writeHeader(current.myRight, out);

      }
	  
    }
   
    //compress and write the body of the out file
    public void writeCompressedBits(BitInputStream in, BitOutputStream out, String[] codings){
    	String code = "";
    	while (true){
        	//read the in file by increments of 8 bits
            int val = in.readBits(BITS_PER_WORD); 
            if (val == -1) break; //at end of the file
            code = codings[val];
            out.writeBits(code.length(), Integer.parseInt(code,2));
            if(myDebugLevel >= DEBUG_HIGH)
            	System.out.printf("writeCompressedBits-> Value %d, coding: %s:  \n", val, code);
        }
       
        //write PSEUDO_EOF to indicate that you're at the end of the file
        code = codings[PSEUDO_EOF];
        out.writeBits(code.length(), Integer.parseInt(code,2));
        if(myDebugLevel >= DEBUG_HIGH)
        	System.out.printf("writeCompressedBits-> Value %d, coding: %s:  \n", PSEUDO_EOF, code);
    }
 
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		//1. Read the 32-bit "magic" number as a check on whether the file is Huffman-coded
		int bits = in.readBits(BITS_PER_INT);
        if (bits  != HUFF_TREE) {
            throw new HuffException("illegal header starts with " + bits);
        }
        
        //2. Read the tree used to de_compress, this is the same tree that was used to
        //compress, recreate tree from header
        HuffNode root = readTreeHeader(in);
        if(myDebugLevel >= DEBUG_HIGH)
        	printNode(root, 0);
        
        //3. Read the bits from the compressed file and use them to traverse root-to-leaf
        //paths, writing leaf values to the output file. Stop when finding PSEUDO_EOF
        
        readCompressedBits(root, in, out);
        
        //4. Close the output file
		out.close();
	}
	
	// recreate tree from header
    public HuffNode readTreeHeader(BitInputStream in){
        int bit = in.readBits(1);
        
        if (bit == -1) 
            throw new HuffException("bad input, no PSEUDO_EOF");
            
        //if bit is 0, recursively call itself to go down the left and right subtrees
        if(bit == 0){
        	HuffNode left = readTreeHeader(in);
            HuffNode right = readTreeHeader(in);
            return new HuffNode(0, 0, left, right);

        }
        if(bit == 1){ //if bit is 1,  arrived at a leaf
        	
        	//read in the 9 bits to get the character value for that leave 
        	//and put it in the HuffNode
        	int value = in.readBits(BITS_PER_WORD+1);
            //int value = in.readBits(9);
            if(myDebugLevel >= DEBUG_HIGH)
            	System.out.printf("readTreeHeader-> Value %d, Char: %c \n", value, (char)value);
            return new HuffNode(value,0);
        }
        
        return null;
    }
    
    /*
     *  Read the bits from the compressed file and use them to traverse root-to-leaf
     *  paths, writing leaf values to the output file. Stop when finding PSEUDO_EOF
     * 
     */
    public void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out){
        HuffNode current = root; //initialize current node to root
       while(true){
            int bit = in.readBits(1);
            if (bit == -1) 
                throw new HuffException("bad input, no PSEUDO_EOF");
            else
            if (bit == 0){
                current = current.myLeft; //0 is left
            }
            else{
                current = current.myRight; //1 is right
            }
            if (current.myLeft == null && current.myRight == null){
                if (current.myValue == PSEUDO_EOF)
                    break;
                else{
	                out.writeBits(BITS_PER_WORD, current.myValue);
	                if(myDebugLevel >= DEBUG_HIGH)
	                    System.out.printf("Read value %d:  char %c \n", current.myValue, (char)current.myValue);
	                current = root;
                }
            }
       }//While loop
    }
    
    public void printNode(HuffNode root, int n) {

    	HuffNode l = root.myLeft;
    	HuffNode r = root.myRight;
    	
    	if(l!=null && r!=null) {
    		System.out.printf("root->node# %d: ", n);
			System.out.printf("Value: %d, Weight: %d, left: %d, leftweight: %d, right: %d, rightweight: %d \n", 
						root.myValue, root.myWeight, root.myLeft.myValue,root.myLeft.myWeight, root.myRight.myValue, root.myRight.myWeight);
			printNode(root.myLeft, n+1);
			printNode(root.myRight, n+2);
    	}
       	
       	if(l!=null && r==null) {
       		System.out.printf("Left->node# %d: ", n);
			System.out.printf("Value: %d, Weight: %d, left: %d, leftweight: %d \n", 
						root.myValue, root.myWeight, root.myLeft.myValue, root.myLeft.myWeight);
			printNode(root.myLeft, n+1);
       	}
       	
       	if(l==null && r!=null) {
       		System.out.printf("right->node# %d: ", n);
			System.out.printf("Value: %d, Weight: %d, right: %d, rightweight: %d \n", 
						root.myValue, root.myWeight,root.myRight.myValue, root.myRight.myWeight);
			printNode(root.myRight, n+1);
       	}
       	
       	if(l==null && r==null) {
       		System.out.printf("Leaf->node# %d: ", n);
    		System.out.printf("Leaf->: Value: %d, Weight: %d\n", root.myValue, root.myWeight);
       	}
       	
       	System.out.println();
    	
    }
    
	 static void printList(String [] a) {
		 int ct = 0;
		 for (int i = 0; i < a.length; i++)
			 if(a[i]!=null) {
				System.out.println(a[i]);
				ct++;
			 }
		 System.out.println("valid elements#: " + ct);
		 System.out.println("Array Length: " + a.length);
	 }
	 
	 static void printIntList(int [] a) {
		 int ct = 0;
		 for (int i = 0; i < a.length; i++)
			 if(a[i]!=0) {
				System.out.printf("elenent: %d, value: %d \n", i, a[i]);
				ct++;
			 }
		 System.out.println("valid elements#: " + ct);
		 System.out.println("Array Length: " + a.length);
	 }
}