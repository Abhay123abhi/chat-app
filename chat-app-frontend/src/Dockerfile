# Use an official Node.js runtime as the base image
FROM node:20 AS build

# Set the working directory in the container
WORKDIR /app

# Copy package.json and package-lock.json to the working directory
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy the rest of the application code
COPY . .

# Build the application
RUN npm run build

# Use an official nginx image to serve the built files
FROM nginx:alpine

# Copy the built files from the previous stage to the nginx web server
COPY --from=build /app/dist /usr/share/nginx/html

# Expose the port nginx will serve on
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]