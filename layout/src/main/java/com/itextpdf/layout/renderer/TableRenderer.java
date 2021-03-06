/*

    This file is part of the iText (R) project.
    Copyright (c) 1998-2016 iText Group NV
    Authors: Bruno Lowagie, Paulo Soares, et al.

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation with the addition of the
    following permission added to Section 15 as permitted in Section 7(a):
    FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
    ITEXT GROUP. ITEXT GROUP DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
    OF THIRD PARTY RIGHTS

    This program is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License
    along with this program; if not, see http://www.gnu.org/licenses or write to
    the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
    Boston, MA, 02110-1301 USA, or download the license from the following URL:
    http://itextpdf.com/terms-of-use/

    The interactive user interfaces in modified source and object code versions
    of this program must display Appropriate Legal Notices, as required under
    Section 5 of the GNU Affero General Public License.

    In accordance with Section 7(b) of the GNU Affero General Public License,
    a covered work must retain the producer line in every PDF that is created
    or manipulated using iText.

    You can be released from the requirements of the license by purchasing
    a commercial license. Buying such a license is mandatory as soon as you
    develop commercial activities involving the iText software without
    disclosing the source code of your own applications.
    These activities include: offering paid services to customers as an ASP,
    serving PDFs on the fly in a web application, shipping iText with a closed
    source product.

    For more information, please contact iText Software Corp. at this
    address: sales@itextpdf.com
 */
package com.itextpdf.layout.renderer;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfVersion;
import com.itextpdf.kernel.pdf.canvas.CanvasArtifact;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.tagutils.IAccessibleElement;
import com.itextpdf.kernel.pdf.tagutils.TagStructureContext;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.itextpdf.layout.border.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.layout.LayoutArea;
import com.itextpdf.layout.layout.LayoutContext;
import com.itextpdf.layout.layout.LayoutResult;
import com.itextpdf.layout.property.Property;
import com.itextpdf.layout.property.VerticalAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class represents the {@link IRenderer renderer} object for a {@link Table}
 * object. It will delegate its drawing operations on to the {@link CellRenderer}
 * instances associated with the {@link Cell table cells}.
 */
public class TableRenderer extends AbstractRenderer {

    protected List<CellRenderer[]> rows = new ArrayList<>();
    // Row range of the current renderer. For large tables it may contain only a few rows.
    protected Table.RowRange rowRange;
    protected TableRenderer headerRenderer;
    protected TableRenderer footerRenderer;
    /**
     * True for newly created renderer. For split renderers this is set to false. Used for tricky layout.
     **/
    protected boolean isOriginalNonSplitRenderer = true;
    private ArrayList<ArrayList<Border>> horizontalBorders;
    private ArrayList<ArrayList<Border>> verticalBorders;
    private float[] columnWidths = null;
    private List<Float> heights = new ArrayList<>();

    private TableRenderer() {
    }

    /**
     * Creates a TableRenderer from a {@link Table} which will partially render
     * the table.
     *
     * @param modelElement the table to be rendered by this renderer
     * @param rowRange     the table rows to be rendered
     */
    public TableRenderer(Table modelElement, Table.RowRange rowRange) {
        super(modelElement);
        setRowRange(rowRange);
    }

    /**
     * Creates a TableRenderer from a {@link Table}.
     *
     * @param modelElement the table to be rendered by this renderer
     */
    public TableRenderer(Table modelElement) {
        this(modelElement, new Table.RowRange(0, modelElement.getNumberOfRows() - 1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addChild(IRenderer renderer) {
        if (renderer instanceof CellRenderer) {
            // In case rowspan or colspan save cell into bottom left corner.
            // In in this case it will be easier handle row heights in case rowspan.
            Cell cell = (Cell) renderer.getModelElement();
            rows.get(cell.getRow() - rowRange.getStartRow() + cell.getRowspan() - 1)[cell.getCol()] = (CellRenderer) renderer;
        } else {
            Logger logger = LoggerFactory.getLogger(TableRenderer.class);
            logger.error("Only BlockRenderer with Cell layout element could be added");
        }
    }

    @Override
    protected Rectangle applyBorderBox(Rectangle rect, Border[] borders, boolean reverse) {
        // Do nothing here. Applying border box for tables is indeed difficult operation and is done on #layout()
        return rect;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LayoutResult layout(LayoutContext layoutContext) {
        LayoutArea area = layoutContext.getArea();
        Rectangle layoutBox = area.getBBox().clone();
        if (!((Table) modelElement).isComplete()) {
            setProperty(Property.MARGIN_BOTTOM, 0);
        }
        if (rowRange.getStartRow() != 0) {
            setProperty(Property.MARGIN_TOP, 0);
        }

        // we can invoke #layout() twice (processing KEEP_TOGETHER for instance)
        // so we need to clear the results of previous #layout() invocation
        heights.clear();
        childRenderers.clear();

        // Cells' up moves occured while split processing
        // key is column number (there can be only one move during one split)
        // value is the previous row number of the cell
        Map<Integer, Integer> rowMoves = new HashMap<Integer, Integer>();

        applyMargins(layoutBox, false);

        Border[] borders;
        float leftTableBorderWidth = -1;
        float rightTableBorderWidth = -1;
        float topTableBorderWidth = -1;
        float bottomTableBorderWidth = 0;

        // Find left, right and top collapsed borders widths.
        // In order to find left and right border widths we try to consider as few rows ad possible
        // i.e. the borders still can be drawn outside the layout area.
        int row = 0;
        while (row < rows.size() && (-1 == leftTableBorderWidth || -1 == rightTableBorderWidth)) {
            CellRenderer[] currentRow = rows.get(row);
            if (0 == row) {
                for (int i = 0; i < currentRow.length; i++) {
                    if (null != currentRow[i]) {
                        borders = currentRow[i].getBorders();
                        topTableBorderWidth = Math.max(null == borders[0] ? -1 : borders[0].getWidth(), topTableBorderWidth);
                    }
                }
            }
            if (0 != currentRow.length) {
                if (null != currentRow[0]) {
                    borders = currentRow[0].getBorders();
                    leftTableBorderWidth = Math.max(null == borders[3] ? -1 : borders[3].getWidth(), leftTableBorderWidth);
                }
                // the last cell in a row can have big rowspan
                int lastInRow = currentRow.length-1;
                while (lastInRow >= 0 && null == currentRow[lastInRow]) {
                    lastInRow--;
                }
                if (lastInRow >= 0 && currentRow.length == lastInRow + currentRow[lastInRow].getPropertyAsInteger(Property.ROWSPAN)) {
                    borders = currentRow[lastInRow].getBorders();
                    rightTableBorderWidth = Math.max(null == borders[1] ? -1 : borders[1].getWidth(), rightTableBorderWidth);
                }
            }
            row++;
        }
        // collapse with table borders
        borders = getBorders();
        leftTableBorderWidth = Math.max(null == borders[3] ? 0 : borders[3].getWidth(), leftTableBorderWidth);
        rightTableBorderWidth = Math.max(null == borders[1] ? 0 : borders[1].getWidth(), rightTableBorderWidth);
        topTableBorderWidth = Math.max(null == borders[0] ? 0 : borders[0].getWidth(), topTableBorderWidth);

        if (isPositioned()) {
            float x = (float) this.getPropertyAsFloat(Property.X);
            float relativeX = isFixedLayout() ? 0 : layoutBox.getX();
            layoutBox.setX(relativeX + x);
        }

        Table tableModel = (Table) getModelElement();

        Float tableWidth = retrieveWidth(layoutBox.getWidth());
        if (tableWidth == null || tableWidth == 0) {
            tableWidth = layoutBox.getWidth();
        }
        occupiedArea = new LayoutArea(area.getPageNumber(),
                new Rectangle(layoutBox.getX(), layoutBox.getY() + layoutBox.getHeight() - topTableBorderWidth / 2, (float) tableWidth, 0));

        int numberOfColumns = ((Table) getModelElement()).getNumberOfColumns();
        horizontalBorders = new ArrayList<>();
        verticalBorders = new ArrayList<>();

        Table headerElement = tableModel.getHeader();
        boolean isFirstHeader = rowRange.getStartRow() == 0 && isOriginalNonSplitRenderer;
        boolean headerShouldBeApplied = !rows.isEmpty() && (!isOriginalNonSplitRenderer || isFirstHeader && !tableModel.isSkipFirstHeader());

        if (headerElement != null && headerShouldBeApplied) {
            headerElement.setBorderTop(borders[0]);
            headerElement.setBorderRight(borders[1]);
            headerElement.setBorderBottom(borders[2]);
            headerElement.setBorderLeft(borders[3]);

            headerRenderer = (TableRenderer) headerElement.createRendererSubTree().setParent(this);
            LayoutResult result = headerRenderer.layout(new LayoutContext(new LayoutArea(area.getPageNumber(), layoutBox)));
            if (result.getStatus() != LayoutResult.FULL) {
                return new LayoutResult(LayoutResult.NOTHING, null, null, this, result.getCauseOfNothing());
            }
            float headerHeight = result.getOccupiedArea().getBBox().getHeight();
            layoutBox.decreaseHeight(headerHeight);
            occupiedArea.getBBox().moveDown(headerHeight).increaseHeight(headerHeight);
        }
        Table footerElement = tableModel.getFooter();
        if (footerElement != null) {
            footerElement.setBorderTop(borders[0]);
            footerElement.setBorderRight(borders[1]);
            footerElement.setBorderBottom(borders[2]);
            footerElement.setBorderLeft(borders[3]);

            footerRenderer = (TableRenderer) footerElement.createRendererSubTree().setParent(this);
            LayoutResult result = footerRenderer.layout(new LayoutContext(new LayoutArea(area.getPageNumber(), layoutBox)));
            if (result.getStatus() != LayoutResult.FULL) {
                return new LayoutResult(LayoutResult.NOTHING, null, null, this, result.getCauseOfNothing());
            }
            float footerHeight = result.getOccupiedArea().getBBox().getHeight();
            footerRenderer.move(0, -(layoutBox.getHeight() - footerHeight));
            layoutBox.moveUp(footerHeight).decreaseHeight(footerHeight);
        }

        // Apply halves of the borders. The other halves are applied on a Cell level
        layoutBox.<Rectangle>applyMargins(topTableBorderWidth / 2, rightTableBorderWidth / 2, 0, leftTableBorderWidth / 2, false);

        columnWidths = calculateScaledColumnWidths(tableModel, (float) tableWidth, leftTableBorderWidth, rightTableBorderWidth);
        LayoutResult[] splits = new LayoutResult[tableModel.getNumberOfColumns()];
        // This represents the target row index for the overflow renderer to be placed to.
        // Usually this is just the current row id of a cell, but it has valuable meaning when a cell has rowspan.
        int[] targetOverflowRowIndex = new int[tableModel.getNumberOfColumns()];

        // complete table with empty cells
        CellRenderer[] lastAddedRow;
        if (0 != rows.size() && null != rows.get(rows.size() - 1)) {
            lastAddedRow = rows.get(rows.size() - 1);
            int colIndex = 0;
            while (colIndex < lastAddedRow.length && null != lastAddedRow[colIndex]) {
                colIndex += (int) lastAddedRow[colIndex].getPropertyAsInteger(Property.COLSPAN);
            }
            // complete row if it's not already complete ot totally empty
            if (0 != colIndex && lastAddedRow.length != colIndex) {
                while (colIndex < lastAddedRow.length) {
                    Cell emptyCell = new Cell();
                    emptyCell.setBorder(Border.NO_BORDER);
                    ((Table) this.getModelElement()).addCell(emptyCell);
                    this.addChild(emptyCell.getRenderer());
                    colIndex++;
                }
            }
        }
        horizontalBorders.add(tableModel.getLastRowBottomBorder());

        for (row = 0; row < rows.size(); row++) {
            // if forced placement was earlier set, this means the element did not fit into the area, and in this case
            // we only want to place the first row in a forced way, not the next ones, otherwise they will be invisible
            if (row == 1 && Boolean.TRUE.equals(this.<Boolean>getOwnProperty(Property.FORCED_PLACEMENT))) {
                deleteOwnProperty(Property.FORCED_PLACEMENT);
            }

            // the width of the widest bottom border of the row
            bottomTableBorderWidth = 0;

            CellRenderer[] currentRow = rows.get(row);
            float rowHeight = 0;
            boolean split = false;
            // Indicates that all the cells fit (at least partially after splitting if not forbidden by keepTogether) in the current row.
            boolean hasContent = true;
            // Indicates that we have added a cell from the future, i.e. a cell which has a big rowspan and we shouldn't have
            // added it yet, because we add a cell with rowspan only during the processing of the very last row this cell occupied,
            // but now we have area break and we had to force that cell addition.
            boolean cellWithBigRowspanAdded = false;
            List<CellRenderer> currChildRenderers = new ArrayList<>();
            // Process in a queue, because we might need to add a cell from the future, i.e. having big rowspan in case of split.
            Deque<CellRendererInfo> cellProcessingQueue = new ArrayDeque<CellRendererInfo>();
            for (int col = 0; col < currentRow.length; col++) {
                if (currentRow[col] != null) {
                    cellProcessingQueue.addLast(new CellRendererInfo(currentRow[col], col, row));
                }
            }
            // the element which was the first to cause Layout.Nothing
            IRenderer firstCauseOfNothing = null;

            while (cellProcessingQueue.size() > 0) {
                CellRendererInfo currentCellInfo = cellProcessingQueue.pop();
                int col = currentCellInfo.column;
                CellRenderer cell = currentCellInfo.cellRenderer;
                int colspan = (int) cell.getPropertyAsInteger(Property.COLSPAN);
                int rowspan = (int) cell.getPropertyAsInteger(Property.ROWSPAN);

                // collapse boundary borders if necessary
                // notice that bottom border collapse is handled afterwards
                Border[] cellBorders = cell.getBorders();
                if (0 == row - rowspan + 1) {
                    cell.setProperty(Property.BORDER_TOP, getCollapsedBorder(cellBorders[0], borders[0]));
                }
                if (0 == col) {
                    cell.setProperty(Property.BORDER_LEFT, getCollapsedBorder(cellBorders[3], borders[3]));
                }
                if (tableModel.getNumberOfColumns() == col + colspan) {
                    cell.setProperty(Property.BORDER_RIGHT, getCollapsedBorder(cellBorders[1], borders[1]));
                }

                if (cell != null) {
                    buildBordersArrays(cell, row, true);
                }
                if (row + 1 < rows.size()) {
                    for (int j = 0; j < cell.getModelElement().getColspan(); j++) {
                        CellRenderer nextCell = rows.get(row + 1)[col + j];
                        if (nextCell != null) {
                            buildBordersArrays(nextCell, row + 1, true);
                        }
                    }
                }
                if (col + 1 < rows.get(row).length) {
                    CellRenderer nextCell = rows.get(row)[col + 1];
                    if (nextCell != null) {
                        buildBordersArrays(nextCell, row, true);
                    }
                }

                targetOverflowRowIndex[col] = currentCellInfo.finishRowInd;
                // This cell came from the future (split occurred and we need to place cell with big rowpsan into the current area)
                boolean currentCellHasBigRowspan = (row != currentCellInfo.finishRowInd);

                float cellWidth = 0, colOffset = 0;
                for (int i = col; i < col + colspan; i++) {
                    cellWidth += columnWidths[i];
                }
                for (int i = 0; i < col; i++) {
                    colOffset += columnWidths[i];
                }
                float rowspanOffset = 0;
                for (int i = row - 1; i > currentCellInfo.finishRowInd - rowspan && i >= 0; i--) {
                    rowspanOffset += (float) heights.get(i);
                }
                float cellLayoutBoxHeight = rowspanOffset + (!currentCellHasBigRowspan || hasContent ? layoutBox.getHeight() : 0);
                float cellLayoutBoxBottom = layoutBox.getY() + (!currentCellHasBigRowspan || hasContent ? 0 : layoutBox.getHeight());
                Rectangle cellLayoutBox = new Rectangle(layoutBox.getX() + colOffset, cellLayoutBoxBottom, cellWidth, cellLayoutBoxHeight);
                LayoutArea cellArea = new LayoutArea(layoutContext.getArea().getPageNumber(), cellLayoutBox);
                VerticalAlignment verticalAlignment = cell.<VerticalAlignment>getProperty(Property.VERTICAL_ALIGNMENT);
                cell.setProperty(Property.VERTICAL_ALIGNMENT, null);

                // Increase bottom borders widths up to the table's if necessary to perform #layout() correctly
                Border oldBottomBorder = cell.getBorders()[2];
                Border collapsedBottomBorder = getCollapsedBorder(oldBottomBorder, borders[2]);
                if (collapsedBottomBorder != null) {
                    bottomTableBorderWidth = Math.max(bottomTableBorderWidth, collapsedBottomBorder.getWidth());
                    cellArea.getBBox().<Rectangle>applyMargins(0, 0, collapsedBottomBorder.getWidth() / 2, 0, false);
                    cell.setProperty(Property.BORDER_BOTTOM, collapsedBottomBorder);
                }
                LayoutResult cellResult = cell.setParent(this).layout(new LayoutContext(cellArea));
                if (collapsedBottomBorder != null && null != cellResult.getOccupiedArea()) {
                    // apply the difference between collapsed table border and own cell border
                    cellResult.getOccupiedArea().getBBox().<Rectangle>applyMargins(0, 0,
                            (collapsedBottomBorder.getWidth() - (oldBottomBorder == null ? 0 : oldBottomBorder.getWidth())) / 2, 0, false);
                    cell.setProperty(Property.BORDER_BOTTOM, oldBottomBorder);
                }
                cell.setProperty(Property.VERTICAL_ALIGNMENT, verticalAlignment);
                // width of BlockRenderer depends on child areas, while in cell case it is hardly define.
                if (cellResult.getStatus() != LayoutResult.NOTHING) {
                    cell.getOccupiedArea().getBBox().setWidth(cellWidth);
                } else if (null == firstCauseOfNothing) {
                    firstCauseOfNothing = cellResult.getCauseOfNothing();
                }

                if (currentCellHasBigRowspan) {
                    // cell from the future
                    if (cellResult.getStatus() == LayoutResult.PARTIAL) {
                        splits[col] = cellResult;
                        currentRow[col] = (CellRenderer) cellResult.getSplitRenderer();
                    } else {
                        rows.get(currentCellInfo.finishRowInd)[col] = null;
                        currentRow[col] = cell;
                        rowMoves.put(col, currentCellInfo.finishRowInd);
                    }
                } else {
                    if (cellResult.getStatus() != LayoutResult.FULL) {
                        // first time split occurs
                        if (!split) {
                            // This is a case when last footer should be skipped and we might face an end of the table.
                            // We check if we can fit all the rows right now and the split occurred only because we reserved
                            // space for footer before, and if yes we skip footer and write all the content right now.
                            if (footerRenderer != null && tableModel.isSkipLastFooter() && tableModel.isComplete()) {
                                LayoutArea potentialArea = new LayoutArea(area.getPageNumber(), layoutBox.clone());
                                float footerHeight = footerRenderer.getOccupiedArea().getBBox().getHeight();
                                potentialArea.getBBox().moveDown(footerHeight).increaseHeight(footerHeight);
                                if (canFitRowsInGivenArea(potentialArea, row, columnWidths, heights)) {
                                    layoutBox.increaseHeight(footerHeight).moveDown(footerHeight);
                                    cellProcessingQueue.clear();
                                    for (int addCol = 0; addCol < currentRow.length; addCol++) {
                                        if (currentRow[addCol] != null) {
                                            cellProcessingQueue.addLast(new CellRendererInfo(currentRow[addCol], addCol, row));
                                        }
                                    }
                                    footerRenderer = null;
                                    continue;
                                }
                            }

                            // Here we look for a cell with big rowpsan (i.e. one which would not be normally processed in
                            // the scope of this row), and we add such cells to the queue, because we need to write them
                            // at least partially into the available area we have.
                            for (int addCol = 0; addCol < currentRow.length; addCol++) {
                                if (currentRow[addCol] == null) {
                                    // Search for the next cell including rowspan.
                                    for (int addRow = row + 1; addRow < rows.size(); addRow++) {
                                        if (rows.get(addRow)[addCol] != null) {
                                            CellRenderer addRenderer = rows.get(addRow)[addCol];
                                            verticalAlignment = addRenderer.<VerticalAlignment>getProperty(Property.VERTICAL_ALIGNMENT);
                                            if (verticalAlignment != null && verticalAlignment.equals(VerticalAlignment.BOTTOM)) {
                                                if (row + addRenderer.getPropertyAsInteger(Property.ROWSPAN) - 1 < addRow) {
                                                    cellProcessingQueue.addLast(new CellRendererInfo(addRenderer, addCol, addRow));
                                                    cellWithBigRowspanAdded = true;
                                                } else {
                                                    horizontalBorders.get(row + 1).set(addCol, addRenderer.getBorders()[2]);
                                                    if (addCol == 0) {
                                                        for (int i = row; i >= 0; i--) {
                                                            if (!checkAndReplaceBorderInArray(verticalBorders, addCol, i, addRenderer.getBorders()[3])) {
                                                                break;
                                                            }
                                                        }
                                                    } else if (addCol == numberOfColumns - 1) {
                                                        for (int i = row; i >= 0; i--) {
                                                            if (!checkAndReplaceBorderInArray(verticalBorders, addCol + 1, i, addRenderer.getBorders()[1])) {
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            } else if (row + addRenderer.getPropertyAsInteger(Property.ROWSPAN) - 1 >= addRow) {
                                                cellProcessingQueue.addLast(new CellRendererInfo(addRenderer, addCol, addRow));
                                                cellWithBigRowspanAdded = true;
                                            }
                                            break;
                                        }
                                    }
                                } else {
                                    // if cell in current row has big rowspan
                                    // we need to process it specially too,
                                    // because some problems (for instance, borders related) can occur
                                    if (cell.getModelElement().getRowspan() > 1) {
                                        cellWithBigRowspanAdded = true;
                                    }
                                }
                            }
                        }
                        split = true;
                        if (cellResult.getStatus() == LayoutResult.NOTHING) {
                            hasContent = false;
                        }
                        splits[col] = cellResult;
                    }
                }
                currChildRenderers.add(cell);
                if (cellResult.getStatus() != LayoutResult.NOTHING) {
                    rowHeight = Math.max(rowHeight, cell.getOccupiedArea().getBBox().getHeight() - rowspanOffset);
                }
            }

            if (hasContent || cellWithBigRowspanAdded) {
                heights.add(rowHeight);
                occupiedArea.getBBox().moveDown(rowHeight);
                occupiedArea.getBBox().increaseHeight(rowHeight);
                layoutBox.decreaseHeight(rowHeight);
            }



            if (split || row == rows.size()-1) {
                // Correct layout area of the last row rendered on the page
                if (heights.size() != 0) {
                    float bottomBorderWidthDifference = 0;
                    if (hasContent || cellWithBigRowspanAdded) {
                        lastAddedRow = currentRow;
                    } else {
                        lastAddedRow = rows.get(row-1);
                    }
                    float currentCellHeight;
                    for (int col = 0; col < lastAddedRow.length; col++) {
                        if (null != lastAddedRow[col]) {
                            currentCellHeight = 0;
                            Border cellBottomBorder = lastAddedRow[col].getBorders()[2];
                            Border collapsedBorder = getCollapsedBorder(cellBottomBorder, borders[2]);
                            float cellBottomBorderWidth = null == cellBottomBorder ? 0 : cellBottomBorder.getWidth();
                            float collapsedBorderWidth = null == collapsedBorder ? 0 : collapsedBorder.getWidth();
                            if (cellBottomBorderWidth < collapsedBorderWidth) {
                                lastAddedRow[col].setProperty(Property.BORDER_BOTTOM, collapsedBorder);
                                horizontalBorders.get(hasContent || cellWithBigRowspanAdded ? row + 1 : row).set(col, collapsedBorder);
                            }
                            // apply the difference between collapsed table border and own cell border
                            lastAddedRow[col].occupiedArea.getBBox().<Rectangle>applyMargins(0, 0,
                                    (collapsedBorderWidth - cellBottomBorderWidth) / 2, 0, true);
                            int cellRowStartIndex =  heights.size()- (int)lastAddedRow[col].getPropertyAsInteger(Property.ROWSPAN);
                            for (int i = cellRowStartIndex > 0 ? cellRowStartIndex : 0 ; i < heights.size(); i++) {
                                currentCellHeight += heights.get(i);
                            }
                            if (currentCellHeight < lastAddedRow[col].occupiedArea.getBBox().getHeight()) {
                                bottomBorderWidthDifference = Math.max(bottomBorderWidthDifference, (collapsedBorderWidth - cellBottomBorderWidth) / 2);
                            }
                        }
                    }
                    heights.set(heights.size() - 1, heights.get(heights.size()-1)+bottomBorderWidthDifference);
                }

                // Correct occupied areas of all added cells
                for (int k = 0; k <= row; k++) {
                    currentRow = rows.get(k);
                    if (k < row || (k == row && (hasContent || cellWithBigRowspanAdded))) {
                        for (int col = 0; col < currentRow.length; col++) {
                            CellRenderer cell = currentRow[col];
                            if (cell == null) {
                                continue;
                            }
                            float height = 0;
                            int rowspan = (int) cell.getPropertyAsInteger(Property.ROWSPAN);
                            for (int i = k; i > ((k == row+1) ? targetOverflowRowIndex[col] : k) - rowspan && i >= 0; i--) {
                                height += (float) heights.get(i);
                            }
                            int rowN = k + 1;
                            if (k == row && !hasContent) {
                                rowN--;
                            }
                            if (horizontalBorders.get(rowN).get(col) == null) {
                                horizontalBorders.get(rowN).set(col, cell.getBorders()[2]);
                            }

                            // Correcting cell bbox only. We don't need #move() here.
                            // This is because of BlockRenderer's specificity regarding occupied area.
                            float shift = height - cell.getOccupiedArea().getBBox().getHeight();
                            Rectangle bBox = cell.getOccupiedArea().getBBox();
                            bBox.moveDown(shift);
                            bBox.setHeight(height);
                            cell.applyVerticalAlignment();
                        }
                    }
                }
                currentRow = rows.get(row);
            }

            if (split) {
                TableRenderer[] splitResult = split(row, hasContent);

                // Apply bottom border
                splitResult[0].getOccupiedArea().getBBox().<Rectangle>applyMargins(0, 0, bottomTableBorderWidth / 2, 0, true);

                int[] rowspans = new int[currentRow.length];
                boolean[] columnsWithCellToBeEnlarged = new boolean[currentRow.length];
                for (int col = 0; col < currentRow.length; col++) {
                    if (splits[col] != null) {
                        CellRenderer cellSplit = (CellRenderer) splits[col].getSplitRenderer();
                        if (null != cellSplit) {
                            rowspans[col] = cellSplit.getModelElement().getRowspan();
                        }
                        if (splits[col].getStatus() != LayoutResult.NOTHING && (hasContent || cellWithBigRowspanAdded)) {
                            childRenderers.add(cellSplit);
                        }
                        if (hasContent || cellWithBigRowspanAdded || splits[col].getStatus() == LayoutResult.NOTHING) {
                            currentRow[col] = null;
                            CellRenderer cellOverflow = (CellRenderer) splits[col].getOverflowRenderer();
                            if (splits[col].getStatus() == LayoutResult.PARTIAL) {
                                cellOverflow.setBorders(cellOverflow.getModelElement().hasProperty(Property.BORDER_BOTTOM) && null == cellOverflow.getModelElement().<Border>getProperty(Property.BORDER_BOTTOM)
                                        ? null
                                        : (Border) cellOverflow.getModelElement().<Border>getDefaultProperty(Property.BORDER), 0);
                            } else {
                                cellOverflow.deleteOwnProperty(Property.BORDER_TOP);
                            }
                            horizontalBorders.get(row + 1).set(col, getBorders()[2] == null
                                    ? cellOverflow.getModelElement().hasProperty(Property.BORDER_BOTTOM) && null == cellOverflow.getModelElement().<Border>getProperty(Property.BORDER_BOTTOM)
                                            ? null
                                            : (Border) cellOverflow.getModelElement().<Border>getDefaultProperty(Property.BORDER)
                                    : getBorders()[2]);
                            cellOverflow.deleteOwnProperty(Property.BORDER_BOTTOM);
                            cellOverflow.setBorders(cellOverflow.getBorders()[2], 2);
                            rows.get(targetOverflowRowIndex[col])[col] = (CellRenderer) cellOverflow.setParent(splitResult[1]);
                        } else {
                            rows.get(targetOverflowRowIndex[col])[col] = (CellRenderer) currentRow[col].setParent(splitResult[1]);
                        }
                    } else if (hasContent && currentRow[col] != null) {
                        columnsWithCellToBeEnlarged[col] = true;
                        horizontalBorders.get(row + 1).set(col, getBorders()[2] == null
                                ? currentRow[col].getModelElement().hasProperty(Property.BORDER_BOTTOM) && null == currentRow[col].getModelElement().<Border>getProperty(Property.BORDER_BOTTOM)
                                        ? null
                                        : (Border) currentRow[col].getModelElement().getDefaultProperty(Property.BORDER)
                                : getBorders()[2]);
                        // for the future
                        currentRow[col].getModelElement().setBorderTop(getBorders()[0] == null
                                ? currentRow[col].getModelElement().hasProperty(Property.BORDER_BOTTOM) && null == currentRow[col].getModelElement().<Border>getProperty(Property.BORDER_BOTTOM)
                                        ? null
                                        : (Border) currentRow[col].getModelElement().<Border>getDefaultProperty(Property.BORDER)
                                : getBorders()[0]);
                    }
                }

                int minRowspan = Integer.MAX_VALUE;
                for (int col = 0; col < rowspans.length; col++) {
                    if (0 != rowspans[col]) {
                        minRowspan = Math.min(minRowspan, rowspans[col]);
                    }
                }

                for (int col = 0; col < numberOfColumns; col++) {
                    if (columnsWithCellToBeEnlarged[col]) {
                        if (1 == minRowspan) {
                            // Here we use the same cell, but create a new renderer which doesn't have any children,
                            // therefore it won't have any content.
                            Cell overflowCell = currentRow[col].getModelElement();
                            currentRow[col].isLastRendererForModelElement = false;
                            childRenderers.add(currentRow[col]);
                            currentRow[col] = null;
                            rows.get(targetOverflowRowIndex[col])[col] = (CellRenderer) overflowCell.getRenderer().setParent(this);
                        } else {
                            childRenderers.add(currentRow[col]);
                            // shift all cells in the column up
                            int i = row;
                            for (; i < row + minRowspan && i + 1 < rows.size() && rows.get(i + 1)[col] != null; i++) {
                                rows.get(i)[col] = rows.get(i + 1)[col];
                                rows.get(i + 1)[col] = null;
                            }
                            // the number of cells behind is less then minRowspan-1
                            // so we should process the last cell in the column as in the case 1 == minRowspan
                            if (i != row + minRowspan - 1 && null != rows.get(i)[col]) {
                                Cell overflowCell = rows.get(i)[col].getModelElement();
                                rows.get(i)[col].isLastRendererForModelElement = false;
                                rows.get(i)[col] = null;
                                rows.get(targetOverflowRowIndex[col])[col] = (CellRenderer) overflowCell.getRenderer().setParent(this);
                            }
                        }
                    }
                }


                if (row == rowRange.getFinishRow() && footerRenderer != null) {
                    footerRenderer.getOccupiedAreaBBox().setY(splitResult[0].getOccupiedAreaBBox().getY()
                            - footerRenderer.getOccupiedAreaBBox().getHeight());
                    for (IRenderer renderer : footerRenderer.getChildRenderers()) {
                        renderer.move(0, splitResult[0].getOccupiedAreaBBox().getY()
                                - renderer.getOccupiedArea().getBBox().getY() - renderer.getOccupiedArea().getBBox().getHeight());
                    }
                } else {
                    adjustFooterAndFixOccupiedArea(layoutBox);
                }

                // On the next page we need to process rows without any changes except moves connected to actual cell splitting
                for (Map.Entry<Integer, Integer> entry : rowMoves.entrySet()) {
                    // Move the cell back to its row if there was no actual split
                    if (null == splitResult[1].rows.get((int) entry.getValue() - splitResult[0].rows.size())[entry.getKey()]) {
                        splitResult[1].rows.get((int) entry.getValue() - splitResult[0].rows.size())[entry.getKey()] = splitResult[1].rows.get(row - splitResult[0].rows.size())[entry.getKey()];
                        splitResult[1].rows.get(row - splitResult[0].rows.size())[entry.getKey()] = null;
                    }
                }

                if (isKeepTogether() && !Boolean.TRUE.equals(getPropertyAsBoolean(Property.FORCED_PLACEMENT))) {
                    return new LayoutResult(LayoutResult.NOTHING, occupiedArea, null, this, null == firstCauseOfNothing ? this : firstCauseOfNothing);
                } else {
                    int status = (childRenderers.isEmpty() && footerRenderer == null)
                            ? LayoutResult.NOTHING
                            : LayoutResult.PARTIAL;
                    if (status == LayoutResult.NOTHING && Boolean.TRUE.equals(getPropertyAsBoolean(Property.FORCED_PLACEMENT))) {
                        return new LayoutResult(LayoutResult.FULL, occupiedArea, null, null);
                    } else {
                        return new LayoutResult(status, occupiedArea, splitResult[0], splitResult[1], LayoutResult.NOTHING == status ? firstCauseOfNothing : null);
                    }
                }
            } else {
                childRenderers.addAll(currChildRenderers);
                currChildRenderers.clear();
            }
        }

        if (isPositioned()) {
            float y = (float) this.getPropertyAsFloat(Property.Y);
            float relativeY = isFixedLayout() ? 0 : layoutBox.getY();
            move(0, relativeY + y - occupiedArea.getBBox().getY());
        }

        // Apply bottom and top border
        applyMargins(occupiedArea.getBBox(), new float[] {topTableBorderWidth / 2, 0, bottomTableBorderWidth / 2, 0}, true);

        applyMargins(occupiedArea.getBBox(), true);
        if (tableModel.isSkipLastFooter() || !tableModel.isComplete()) {
            footerRenderer = null;
        }
        adjustFooterAndFixOccupiedArea(layoutBox);
        return new LayoutResult(LayoutResult.FULL, occupiedArea, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(DrawContext drawContext) {
        PdfDocument document = drawContext.getDocument();
        boolean isTagged = drawContext.isTaggingEnabled() && getModelElement() instanceof IAccessibleElement;
        boolean ignoreTag = false;
        PdfName role = null;
        if (isTagged) {
            role = ((IAccessibleElement) getModelElement()).getRole();
            boolean isHeaderOrFooter = PdfName.THead.equals(role) || PdfName.TFoot.equals(role);
            boolean ignoreHeaderFooterTag =
                    document.getTagStructureContext().getTagStructureTargetVersion().compareTo(PdfVersion.PDF_1_5) < 0;
            ignoreTag = isHeaderOrFooter && ignoreHeaderFooterTag;
        }
        if (role != null
                && !role.equals(PdfName.Artifact)
                && !ignoreTag) {
            TagStructureContext tagStructureContext = document.getTagStructureContext();
            TagTreePointer tagPointer = tagStructureContext.getAutoTaggingPointer();

            IAccessibleElement accessibleElement = (IAccessibleElement) getModelElement();
            if (!tagStructureContext.isElementConnectedToTag(accessibleElement)) {
                AccessibleAttributesApplier.applyLayoutAttributes(role, this, document);
            }

            Table modelElement = (Table) getModelElement();
            tagPointer.addTag(accessibleElement, true);

            super.draw(drawContext);

            tagPointer.moveToParent();

            boolean toRemoveConnectionsWithTag = isLastRendererForModelElement && modelElement.isComplete();
            if (toRemoveConnectionsWithTag) {
                tagPointer.removeElementConnectionToTag(accessibleElement);
            }
        } else {
            super.draw(drawContext);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void drawChildren(DrawContext drawContext) {
        Table modelElement = (Table) getModelElement();
        if (headerRenderer != null) {
            boolean firstHeader = rowRange.getStartRow() == 0 && isOriginalNonSplitRenderer && !modelElement.isSkipFirstHeader();
            boolean notToTagHeader = drawContext.isTaggingEnabled() && !firstHeader;
            if (notToTagHeader) {
                drawContext.setTaggingEnabled(false);
                drawContext.getCanvas().openTag(new CanvasArtifact());
            }
            headerRenderer.draw(drawContext);
            if (notToTagHeader) {
                drawContext.getCanvas().closeTag();
                drawContext.setTaggingEnabled(true);
            }
        }

        boolean isTagged = drawContext.isTaggingEnabled() && getModelElement() instanceof IAccessibleElement && !childRenderers.isEmpty();
        TagTreePointer tagPointer = null;
        boolean shouldHaveFooterOrHeaderTag = modelElement.getHeader() != null || modelElement.getFooter() != null;
        if (isTagged) {
            PdfName role = modelElement.getRole();
            if (role != null && !PdfName.Artifact.equals(role)) {
                tagPointer = drawContext.getDocument().getTagStructureContext().getAutoTaggingPointer();

                boolean ignoreHeaderFooterTag = drawContext.getDocument().getTagStructureContext()
                        .getTagStructureTargetVersion().compareTo(PdfVersion.PDF_1_5) < 0;
                shouldHaveFooterOrHeaderTag = shouldHaveFooterOrHeaderTag && !ignoreHeaderFooterTag
                        && (!modelElement.isSkipFirstHeader() || !modelElement.isSkipLastFooter());
                if (shouldHaveFooterOrHeaderTag) {
                    if (tagPointer.getKidsRoles().contains(PdfName.TBody)) {
                        tagPointer.moveToKid(PdfName.TBody);
                    } else {
                        tagPointer.addTag(PdfName.TBody);
                    }
                }
            } else {
                isTagged = false;
            }
        }

        for (IRenderer child : childRenderers) {
            if (isTagged) {
                int adjustByHeaderRowsNum = 0;
                if (modelElement.getHeader() != null && !modelElement.isSkipFirstHeader() && !shouldHaveFooterOrHeaderTag) {
                    adjustByHeaderRowsNum = modelElement.getHeader().getNumberOfRows();
                }
                int cellRow = ((Cell) child.getModelElement()).getRow() + adjustByHeaderRowsNum;
                int rowsNum = tagPointer.getKidsRoles().size();
                if (cellRow < rowsNum) {
                    tagPointer.moveToKid(cellRow);
                } else {
                    tagPointer.addTag(PdfName.TR);
                }
            }

            child.draw(drawContext);

            if (isTagged) {
                tagPointer.moveToParent();
            }
        }

        if (isTagged) {
            if (shouldHaveFooterOrHeaderTag) {
                tagPointer.moveToParent();
            }
        }

        drawBorders(drawContext);

        if (footerRenderer != null) {
            boolean lastFooter = isLastRendererForModelElement && modelElement.isComplete() && !modelElement.isSkipLastFooter();
            boolean notToTagFooter = drawContext.isTaggingEnabled() && !lastFooter;
            if (notToTagFooter) {
                drawContext.setTaggingEnabled(false);
                drawContext.getCanvas().openTag(new CanvasArtifact());
            }
            footerRenderer.draw(drawContext);
            if (notToTagFooter) {
                drawContext.getCanvas().closeTag();
                drawContext.setTaggingEnabled(true);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IRenderer getNextRenderer() {
        TableRenderer nextTable = new TableRenderer();
        nextTable.modelElement = modelElement;
        return nextTable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void move(float dxRight, float dyUp) {
        super.move(dxRight, dyUp);
        if (headerRenderer != null) {
            headerRenderer.move(dxRight, dyUp);
        }
        if (footerRenderer != null) {
            footerRenderer.move(dxRight, dyUp);
        }
    }

    protected float[] calculateScaledColumnWidths(Table tableModel, float tableWidth, float leftBorderWidth, float rightBorderWidth) {
        float[] columnWidths = new float[tableModel.getNumberOfColumns()];
        float widthSum = 0;
        for (int i = 0; i < tableModel.getNumberOfColumns(); i++) {
            columnWidths[i] = tableModel.getColumnWidth(i);
            widthSum += columnWidths[i];
        }
        for (int i = 0; i < tableModel.getNumberOfColumns(); i++) {
            columnWidths[i] *= (tableWidth - leftBorderWidth / 2 - rightBorderWidth / 2) / widthSum;
        }

        return columnWidths;
    }

    protected TableRenderer[] split(int row) {
        return split(row, false);
    }

    protected TableRenderer[] split(int row, boolean hasContent) {
        TableRenderer splitRenderer = createSplitRenderer(new Table.RowRange(rowRange.getStartRow(), rowRange.getStartRow() + row));
        splitRenderer.rows = rows.subList(0, row);
        int rowN = row;
        if (hasContent || row == 0) {
            rowN++;
        }
        splitRenderer.horizontalBorders = new ArrayList<>();
        //splitRenderer.horizontalBorders.addAll(horizontalBorders);
        for (int i = 0; i <= rowN; i++) {
            splitRenderer.horizontalBorders.add(horizontalBorders.get(i));
        }
        splitRenderer.verticalBorders = new ArrayList<>();
//        splitRenderer.verticalBorders.addAll(verticalBorders);
        for (int i = 0; i < verticalBorders.size(); i++) {
            splitRenderer.verticalBorders.add(new ArrayList<Border>());
            for (int j = 0; j < rowN; j++) {
                if (verticalBorders.get(i).size() != 0) {
                    splitRenderer.verticalBorders.get(i).add(verticalBorders.get(i).get(j));
                }
            }
        }
        splitRenderer.heights = heights;
        splitRenderer.columnWidths = columnWidths;
        TableRenderer overflowRenderer = createOverflowRenderer(new Table.RowRange(rowRange.getStartRow() + row, rowRange.getFinishRow()));
        overflowRenderer.rows = rows.subList(row, rows.size());
        splitRenderer.occupiedArea = occupiedArea;

        return new TableRenderer[]{splitRenderer, overflowRenderer};
    }

    protected TableRenderer createSplitRenderer(Table.RowRange rowRange) {
        TableRenderer splitRenderer = (TableRenderer) getNextRenderer();
        splitRenderer.rowRange = rowRange;
        splitRenderer.parent = parent;
        splitRenderer.modelElement = modelElement;
        // TODO childRenderers will be populated twice during the relayout.
        // We should probably clean them before #layout().
        splitRenderer.childRenderers = childRenderers;
        splitRenderer.addAllProperties(getOwnProperties());
        splitRenderer.headerRenderer = headerRenderer;
        splitRenderer.footerRenderer = footerRenderer;
        splitRenderer.isLastRendererForModelElement = false;
        return splitRenderer;
    }

    protected TableRenderer createOverflowRenderer(Table.RowRange rowRange) {
        TableRenderer overflowRenderer = (TableRenderer) getNextRenderer();
        overflowRenderer.setRowRange(rowRange);
        overflowRenderer.parent = parent;
        overflowRenderer.modelElement = modelElement;
        overflowRenderer.addAllProperties(getOwnProperties());
        overflowRenderer.isOriginalNonSplitRenderer = false;
        return overflowRenderer;
    }

    @Override
    public void drawBorder(DrawContext drawContext) {
        // Do nothing here. Itext7 handles cell and table borders collapse and draws result borders during #drawBorders()
    }

    protected void drawBorders(DrawContext drawContext) {
        if (occupiedArea.getBBox().getHeight() < EPS || childRenderers.size() == 0) {
            return;
        }

        float startX = getOccupiedArea().getBBox().getX();
        float startY = getOccupiedArea().getBBox().getY() + getOccupiedArea().getBBox().getHeight();

        for (IRenderer child : childRenderers) {
            CellRenderer cell = (CellRenderer) child;
            if (cell.getModelElement().getRow() == this.rowRange.getStartRow()) {
                startY = cell.getOccupiedArea().getBBox().getY() + cell.getOccupiedArea().getBBox().getHeight();
                break;
            }
        }

        for (IRenderer child : childRenderers) {
            CellRenderer cell = (CellRenderer) child;
            if (cell.getModelElement().getCol() == 0) {
                startX = cell.getOccupiedArea().getBBox().getX();
                break;
            }
        }

        boolean isTagged = drawContext.isTaggingEnabled() && getModelElement() instanceof IAccessibleElement;
        if (isTagged) {
            drawContext.getCanvas().openTag(new CanvasArtifact());
        }

        // Notice that we draw boundary borders after all the others are drawn
        float y1 = startY;
        if (heights.size() > 0) {
            y1 -= (float) heights.get(0);
        }

        for (int i = 1; i < horizontalBorders.size()-1; i++) {
            drawHorizontalBorder(i, startX, y1, drawContext.getCanvas());
            if (i < heights.size()) {
                y1 -= (float) heights.get(i);
            }
        }

        float x1 = startX;
        if (columnWidths.length > 0) {
            x1 += columnWidths[0];
        }

        for (int i = 1; i < verticalBorders.size(); i++) {
            drawVerticalBorder(i, startY, x1, drawContext.getCanvas());
            if (i < columnWidths.length) {
                x1 += columnWidths[i];
            }
        }
        // draw boundary borders
        drawVerticalBorder(0, startY, startX, drawContext.getCanvas());
        drawHorizontalBorder(0, startX, startY, drawContext.getCanvas());
        y1 = startY;
        for (int i = 0; i < heights.size(); i++) {
            y1 -= heights.get(i);
        }
        drawHorizontalBorder(horizontalBorders.size()-1, startX, y1, drawContext.getCanvas());



        if (isTagged) {
            drawContext.getCanvas().closeTag();
        }
    }

    private void drawHorizontalBorder(int i, float startX, float y1, PdfCanvas canvas) {
        ArrayList<Border> borders = horizontalBorders.get(i);
        float x1 = startX;
        float x2 = x1 + columnWidths[0];
        if (i == 0) {
            if (verticalBorders != null && verticalBorders.size() > 0 && verticalBorders.get(0).size() > 0 && verticalBorders.get(verticalBorders.size() - 1).size() > 0) {
                Border firstBorder = verticalBorders.get(0).get(0);
                if (firstBorder != null) {
                    x1 -= firstBorder.getWidth() / 2;
                }
            }
        } else if (i == horizontalBorders.size() - 1) {
            if (verticalBorders != null && verticalBorders.size() > 0 && verticalBorders.get(0).size() > 0 &&
                    verticalBorders.get(verticalBorders.size() - 1) != null && verticalBorders.get(verticalBorders.size() - 1).size() > 0
                    && verticalBorders.get(0) != null) {
                Border firstBorder = verticalBorders.get(0).get(verticalBorders.get(0).size() - 1);
                if (firstBorder != null) {
                    x1 -= firstBorder.getWidth() / 2;
                }
            }
        }

        int j;
        for (j = 1; j < borders.size(); j++) {
            Border prevBorder = borders.get(j - 1);
            Border curBorder = borders.get(j);
            if (prevBorder != null) {
                if (!prevBorder.equals(curBorder)) {
                    prevBorder.drawCellBorder(canvas, x1, y1, x2, y1);
                    x1 = x2;
                }
            } else {
                x1 += columnWidths[j - 1];
                x2 = x1;
            }
            if (curBorder != null) {
                x2 += columnWidths[j];
            }
        }

        Border lastBorder = borders.size() > j - 1 ? borders.get(j - 1) : null;
        if (lastBorder != null) {
            if (verticalBorders != null && verticalBorders.get(j) != null && verticalBorders.get(j).size() > 0) {
                if (i == 0) {
                    if (verticalBorders.get(j).get(i) != null)
                        x2 += verticalBorders.get(j).get(i).getWidth() / 2;
                } else if (i == horizontalBorders.size() - 1 && verticalBorders.get(j).size() >= i - 1 && verticalBorders.get(j).get(i - 1) != null) {
                    x2 += verticalBorders.get(j).get(i - 1).getWidth() / 2;
                }
            }

            lastBorder.drawCellBorder(canvas, x1, y1, x2, y1);
        }
    }

    private void drawVerticalBorder(int i, float startY, float x1, PdfCanvas canvas) {
        ArrayList<Border> borders = verticalBorders.get(i);
        float y1 = startY;
        float y2 = y1;
        if (!heights.isEmpty()) {
            y2 = y1 - (float) heights.get(0);
        }
        int j;
        for (j = 1; j < borders.size(); j++) {
            Border prevBorder = borders.get(j - 1);
            Border curBorder = borders.get(j);
            if (prevBorder != null) {
                if (!prevBorder.equals(curBorder)) {
                    prevBorder.drawCellBorder(canvas, x1, y1, x1, y2);
                    y1 = y2;
                }
            } else {
                y1 -= (float) heights.get(j - 1);
                y2 = y1;
            }
            if (curBorder != null) {
                y2 -= (float) heights.get(j);
            }
        }
        if (borders.size() == 0) {
            return;
        }
        Border lastBorder = borders.get(j - 1);
        if (lastBorder != null) {
            lastBorder.drawCellBorder(canvas, x1, y1, x1, y2);
        }
    }

    /**
     * If there is some space left, we move footer up, because initially footer will be at the very bottom of the area.
     * We also adjust occupied area by footer size if it is present.
     *
     * @param layoutBox the layout box which represents the area which is left free.
     */
    private void adjustFooterAndFixOccupiedArea(Rectangle layoutBox) {
        if (footerRenderer != null) {
            footerRenderer.move(0, layoutBox.getHeight());
            float footerHeight = footerRenderer.getOccupiedArea().getBBox().getHeight();
            occupiedArea.getBBox().moveDown(footerHeight).increaseHeight(footerHeight);
        }
    }

    /**
     * This method checks if we can completely fit the rows in the given area, staring from the startRow.
     */
    private boolean canFitRowsInGivenArea(LayoutArea layoutArea, int startRow, float[] columnWidths, List<Float> heights) {
        layoutArea = layoutArea.clone();
        heights = new ArrayList<>(heights);
        for (int row = startRow; row < rows.size(); row++) {
            CellRenderer[] rowCells = rows.get(row);
            float rowHeight = 0;
            for (int col = 0; col < rowCells.length; col++) {
                CellRenderer cell = rowCells[col];
                if (cell == null) {
                    continue;
                }

                int colspan = (int) cell.getPropertyAsInteger(Property.COLSPAN);
                int rowspan = (int) cell.getPropertyAsInteger(Property.ROWSPAN);
                float cellWidth = 0, colOffset = 0;
                for (int i = col; i < col + colspan; i++) {
                    cellWidth += columnWidths[i];
                }
                for (int i = 0; i < col; i++) {
                    colOffset += columnWidths[i];
                }
                float rowspanOffset = 0;
                for (int i = row - 1; i > row - rowspan && i >= 0; i--) {
                    rowspanOffset += (float) heights.get(i);
                }
                float cellLayoutBoxHeight = rowspanOffset + layoutArea.getBBox().getHeight();
                Rectangle cellLayoutBox = new Rectangle(layoutArea.getBBox().getX() + colOffset, layoutArea.getBBox().getY(), cellWidth, cellLayoutBoxHeight);
                LayoutArea cellArea = new LayoutArea(layoutArea.getPageNumber(), cellLayoutBox);
                LayoutResult cellResult = cell.setParent(this).layout(new LayoutContext(cellArea));

                if (cellResult.getStatus() != LayoutResult.FULL) {
                    return false;
                }
                rowHeight = Math.max(rowHeight, cellResult.getOccupiedArea().getBBox().getHeight());
            }
            heights.add(rowHeight);
            layoutArea.getBBox().moveUp(rowHeight).decreaseHeight(rowHeight);
        }
        return true;
    }

    private void buildBordersArrays(CellRenderer cell, int row, boolean hasContent) {
        int colspan = (int)cell.getPropertyAsInteger(Property.COLSPAN);
        int rowspan = (int)cell.getPropertyAsInteger(Property.ROWSPAN);
        int colN = cell.getModelElement().getCol();
        Border[] cellBorders = cell.getBorders();
        if (row + 1 - rowspan < 0) {
            rowspan = row + 1;
        }

        if (row + 1 - rowspan != 0) {
            for (int i = 0; i < colspan; i++) {
                if (checkAndReplaceBorderInArray(horizontalBorders, row + 1 - rowspan, colN + i, cellBorders[0])) {
                    CellRenderer rend = rows.get(row - rowspan)[colN];
                    if (rend != null) {
                        rend.setBorders(cellBorders[0], 2);
                    }
                } else {
                    cell.setBorders(horizontalBorders.get(row + 1 - rowspan).get(colN + i), 0);
                }
            }
        } else {
            for (int i = 0; i < colspan; i++) {
                if (!checkAndReplaceBorderInArray(horizontalBorders, 0, colN + i, cellBorders[0])) {
                    cell.setBorders(horizontalBorders.get(0).get(colN + i), 0);
                }
            }
        }
        for (int i = 0; i < colspan; i++) {
            if (hasContent) {
                if (row + 1 == horizontalBorders.size()) {
                    horizontalBorders.add(new ArrayList<Border>());
                }
                ArrayList<Border> borders = horizontalBorders.get(row + 1);
                if (borders.size() <= colN + i) {
                    for (int count = borders.size(); count < colN + i; count++) {
                        borders.add(null);
                    }
                    borders.add(cellBorders[2]);
                } else {
                    if (borders.size() == colN + i) {
                        borders.add(cellBorders[2]);
                    } else {
                        borders.set(colN + i, cellBorders[2]);
                    }
                }
            } else {
                if (row == horizontalBorders.size()) {
                    horizontalBorders.add(new ArrayList<Border>());
                }
                horizontalBorders.get(row).add(colN + i, cellBorders[2]);
            }
        }
        if (rowspan > 1) {
            int numOfColumns = ((Table) getModelElement()).getNumberOfColumns();
            for (int k = row - rowspan + 1; k <= row; k++) {
                ArrayList<Border> borders = horizontalBorders.get(k);
                if (borders.size() < numOfColumns) {
                    for (int j = borders.size(); j < numOfColumns; j++) {
                        borders.add(null);
                    }
                }
            }
        }
        if (colN != 0) {
            for (int j = row - rowspan + 1; j <= row; j++) {
                if (checkAndReplaceBorderInArray(verticalBorders, colN, j, cellBorders[3])) {
                    CellRenderer rend = rows.get(j)[colN - 1];
                    if (rend != null) {
                        rend.setBorders(cellBorders[3], 1);
                    }
                } else {
                    CellRenderer rend = rows.get(j)[colN];
                    if (rend != null) {
                        rend.setBorders(verticalBorders.get(colN).get(row), 3);
                    }
                }
            }
        } else {
            for (int j = row - rowspan + 1; j <= row; j++) {
                if (verticalBorders.isEmpty()) {
                    verticalBorders.add(new ArrayList<Border>());
                }
                if (verticalBorders.get(0).size() <= j) {
                    verticalBorders.get(0).add(cellBorders[3]);
                } else {
                    verticalBorders.get(0).set(j, cellBorders[3]);
                }
            }
        }

        for (int i = row - rowspan + 1; i <= row; i++) {
            checkAndReplaceBorderInArray(verticalBorders, colN + colspan, i, cellBorders[1]);
        }
        if (colspan > 1) {
            for (int k = colN; k <= colspan + colN; k++) {
                ArrayList<Border> borders = verticalBorders.get(k);
                if (borders.size() < row + rowspan) {
                    for (int j = borders.size(); j < row + rowspan; j++) {
                        borders.add(null);
                    }
                }
            }
        }
    }

    /**
     * Returns the collapsed border. We process collapse
     * if the table border width is strictly greater than cell border width.
     *
     * @param cellBorder cell border
     * @param tableBorder table border
     * @return the collapsed border
     */
    private Border getCollapsedBorder(Border cellBorder, Border tableBorder) {
        if (null != tableBorder) {
            if (null == cellBorder || cellBorder.getWidth() < tableBorder.getWidth()) {
                return tableBorder;
            }
        }
        if (null != cellBorder) {
            return cellBorder;
        } else {
            return Border.NO_BORDER;
        }
    }

    private boolean checkAndReplaceBorderInArray(ArrayList<ArrayList<Border>> borderArray, int i, int j, Border borderToAdd) {
        if (borderArray.size() <= i) {
            for (int count = borderArray.size(); count <= i; count++) {
                borderArray.add(new ArrayList<Border>());
            }
        }
        ArrayList<Border> borders = borderArray.get(i);
        if (borders.isEmpty()) {
            for (int count = 0; count < j; count++) {
                borders.add(null);
            }
            borders.add(borderToAdd);
            return true;
        }
        if (borders.size() == j) {
            borders.add(borderToAdd);
            return true;
        }
        if (borders.size() <= j) {
            for (int count = borders.size(); count <= j; count++) {
                borders.add(count, null);
            }
        }
        Border neighbour = borders.get(j);
        if (neighbour == null) {
            borders.set(j, borderToAdd);
            return true;
        } else {
            if (neighbour != borderToAdd) {
                if (borderToAdd != null && neighbour.getWidth() < borderToAdd.getWidth()) {
                    borders.set(j, borderToAdd);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * This method is used to set row range for table renderer during creating a new renderer.
     * The purpose to use this method is to remove input argument RowRange from createOverflowRenderer
     * and createSplitRenderer methods.
     */
    private void setRowRange(Table.RowRange rowRange) {
        this.rowRange = rowRange;
        for (int row = rowRange.getStartRow(); row <= rowRange.getFinishRow(); row++) {
            rows.add(new CellRenderer[((Table) modelElement).getNumberOfColumns()]);
        }
    }


    /**
     * This is a struct used for convenience in layout.
     */
    private static class CellRendererInfo {
        public CellRenderer cellRenderer;
        public int column;
        public int finishRowInd;

        public CellRendererInfo(CellRenderer cellRenderer, int column, int finishRow) {
            this.cellRenderer = cellRenderer;
            this.column = column;
            // When a cell has a rowspan, this is the index of the finish row of the cell.
            // Otherwise, this is simply the index of the row of the cell in the {@link #rows} array.
            this.finishRowInd = finishRow;
        }
    }
}
